package com.aishield.detector.detection

import android.content.Context
import android.graphics.Bitmap
import com.aishield.detector.core.ScoreFusion

/**
 * Orchestrates one analysis pass over a sampled screen bitmap:
 * visual detector -> face/deepfake -> audio snapshot -> fusion.
 *
 * Runs on a background thread owned by ScanService.
 */
class DetectionEngine(context: Context) {

    private val appContext = context.applicationContext
    private val tflite = TfliteImageDetector.createIfAvailable(appContext)
    private val deepfake = DeepfakeAnalyzer(appContext)

    val detectorName: String
        get() = tflite?.name ?: HeuristicImageDetector.name

    data class AnalysisResult(
        val visual: Double,
        val deepfake: Double?,
        val audio: Double?,
        val c2pa: C2paStatus,
        val overall: Double,
        val reasons: List<String>
    )

    fun analyze(
        bitmap: Bitmap,
        @Suppress("UNUSED_PARAMETER") foregroundPackage: String?,
        audio: AudioAnalyzer.AudioSnapshot?
    ): AnalysisResult {
        val visualScore = runCatching {
            tflite?.score(bitmap) ?: HeuristicImageDetector.score(bitmap)
        }.getOrElse { HeuristicImageDetector.score(bitmap) }

        val face = runCatching { FaceHelper.findFaces(bitmap).firstOrNull() }.getOrNull()
        val deepfakeScore = if (face != null) {
            runCatching { deepfake.analyze(bitmap, face) }.getOrNull()
        } else null

        val audioScore = audio?.takeIf { it.speechLikely > 0.45 }?.syntheticProb
        val overall = ScoreFusion.fuse(visualScore, deepfakeScore, audioScore)

        val reasons = buildList {
            add("Visual content: ${pct(visualScore)}% likely synthetic")
            if (deepfakeScore != null) add("Deepfake indicators: ${pct(deepfakeScore)}%")
            if (audioScore != null) add("Synthetic audio: ${pct(audioScore)}%")
            add("Content Credentials: ${C2paChecker.checkFromScreenCapture().display()}")
        }

        return AnalysisResult(
            visual = visualScore,
            deepfake = deepfakeScore,
            audio = audioScore,
            c2pa = C2paChecker.checkFromScreenCapture(),
            overall = overall,
            reasons = reasons
        )
    }

    fun close() {
        try {
            tflite?.close()
        } catch (_: Throwable) {
        }
    }

    private fun pct(v: Double): Int = Math.round(v * 100).toInt()
}
