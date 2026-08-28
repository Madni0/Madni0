package com.aishield.detector.detection

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import kotlin.math.exp
import kotlin.math.ln

/**
 * Analyzes device playback audio (captured via the screen-capture session,
 * Android 10+ AudioPlaybackCapture) for synthetic/robotic voice indicators.
 *
 * This is a lightweight spectral baseline. A trained classifier can replace
 * `analyzeWindow` without touching the capture plumbing.
 *
 * Note: apps can opt out of playback capture (DRM/FLAG_SECURE media will
 * simply not appear here) and the OS shows the user audio is being captured
 * as part of the projection consent dialog - by design.
 */
class AudioAnalyzer(private val projection: MediaProjection) {

    data class AudioSnapshot(val syntheticProb: Double, val speechLikely: Double)

    @Volatile
    private var running = false

    private var record: AudioRecord? = null
    private var thread: Thread? = null

    private val buf = FloatArray(RATE * WINDOW_SECONDS)
    private var writePos = 0
    private var filled = 0
    private val lock = Object()

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "aishield-audio").apply {
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        try {
            thread?.join(400)
        } catch (_: InterruptedException) {
        }
        thread = null
        record?.let { r ->
            try {
                r.stop()
            } catch (_: IllegalStateException) {
            }
            r.release()
        }
        record = null
    }

    private fun loop() {
        try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setSampleRate(RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            val r = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(RATE * 2) // 1s of 16-bit mono
                .setAudioPlaybackCaptureConfig(captureConfig)
                .build()
            record = r
            r.startRecording()

            val tmp = ShortArray(2048)
            while (running) {
                val n = r.read(tmp, 0, tmp.size)
                if (n <= 0) {
                    Thread.sleep(30)
                    continue
                }
                synchronized(lock) {
                    for (i in 0 until n) {
                        buf[writePos] = tmp[i] / 32768f
                        writePos = (writePos + 1) % buf.size
                        if (filled < buf.size) filled++
                    }
                }
            }
        } catch (_: Throwable) {
            // Capture unavailable (app opted out, permission revoked, ...).
            // Stay silent - the visual pipeline is unaffected.
        }
    }

    /** Returns a snapshot if >= 2s of non-silent, speech-like audio was heard. */
    fun snapshot(): AudioSnapshot? {
        val window = synchronized(lock) {
            if (filled < RATE * 2) return null
            val out = FloatArray(RATE * 2)
            val start = (writePos - out.size + buf.size) % buf.size
            for (i in out.indices) out[i] = buf[(start + i) % buf.size]
            out
        } ?: return null
        val snap = analyzeWindow(window)
        return if (snap.speechLikely > 0.45 && snap.syntheticProb > 0.10) snap else null
    }

    companion object {
        const val RATE = 16_000
        const val WINDOW_SECONDS = 5

        /** Pure-JVM testable feature/score computation. */
        fun analyzeWindow(samples: FloatArray): AudioSnapshot {
            var sumSq = 0.0
            for (s in samples) sumSq += s.toDouble() * s
            val rms = kotlin.math.sqrt(sumSq / samples.size)
            if (rms < 0.004) return AudioSnapshot(0.0, 0.0) // silence

            // Frame-wise spectral features (1024-pt FFT, hop 512)
            var flatSum = 0.0
            var centroidSum = 0.0
            var zcrSum = 0.0
            var frames = 0
            val re = DoubleArray(1024)
            val im = DoubleArray(1024)
            var off = 0
            while (off + 1024 <= samples.size) {
                var frameRms = 0.0
                var zc = 0
                for (i in 0 until 1024) {
                    val v = samples[off + i].toDouble()
                    re[i] = v
                    im[i] = 0.0
                    frameRms += v * v
                    if (i > 0 && (samples[off + i] * samples[off + i - 1]) < 0) zc++
                }
                if (frameRms / 1024 > 0.004 * 0.004) {
                    Fft.fft(re, im)
                    var logSum = 0.0
                    var pSum = 0.0
                    var cSum = 0.0
                    var bins = 0
                    for (b in 2 until 400) {
                        val p = re[b] * re[b] + im[b] * im[b]
                        if (p > 1e-14) {
                            logSum += ln(p)
                            bins++
                        }
                        pSum += p
                        cSum += b.toDouble() * p
                    }
                    if (bins > 0 && pSum > 1e-12) {
                        val geo = exp(logSum / bins)
                        val arith = pSum / bins
                        flatSum += (geo / arith).coerceIn(0.0, 1.0)
                        centroidSum += cSum / pSum
                        zcrSum += zc.toDouble() / 1024.0
                        frames++
                    }
                }
                off += 512
            }
            if (frames == 0) return AudioSnapshot(0.0, 0.0)
            val flatMean = flatSum / frames
            val centroid = centroidSum / frames
            val zcrMean = zcrSum / frames

            // Pitch periodicity via normalized autocorrelation peak (downsample x2 -> 8kHz)
            val ds = FloatArray(samples.size / 2)
            for (i in ds.indices) ds[i] = samples[i * 2]
            var best = 0.0
            var energy0 = 0.0
            for (v in ds) energy0 += v.toDouble() * v
            if (energy0 > 1e-9) {
                for (lag in 32..400) { // ~ 62..250 Hz @ 8kHz
                    var acc = 0.0
                    for (i in 0 until ds.size - lag) acc += ds[i].toDouble() * ds[i + lag]
                    val norm = acc / energy0
                    if (norm > best) best = norm
                }
            }

            val centroidNorm = ((centroid - 200.0) / 2500.0).coerceIn(0.0, 1.0)
            val speechLogit = 0.4 + 2.8 * centroidNorm + 1.5 * (best - 0.35).coerceAtLeast(0.0) -
                2.5 * (flatMean - 0.5)
            val speechLikely = (1.0 / (1.0 + exp(-speechLogit))).coerceIn(0.0, 1.0)

            // TTS/synthetic voices: very periodic, low noisiness (flat spectral),
            // stable zero-crossing rate.
            val synthLogit = 0.9 + 3.0 * (best - 0.70) - 2.2 * (flatMean - 0.30) +
                0.8 * (0.06 - zcrMean)
            val synthetic = (1.0 / (1.0 + exp(-synthLogit))).coerceIn(0.0, 1.0)
            return AudioSnapshot(synthetic, speechLikely)
        }
    }
}
