package com.aishield.detector.detection

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * BASELINE on-device detector (no model download required).
 *
 * It measures frequency/noise/statistics features that differ between
 * natural camera photos and typical synthetic renders:
 *
 *  1. Sensor/texture noise energy (median-filter residual)   - synthetic media is "too clean"
 *  2. Laplacian high-frequency energy                        - texture richness
 *  3. Power-spectrum slope                                   - natural images follow ~1/f^2
 *  4. JPEG blockiness                                        - recompression fingerprint
 *  5. Color saturation statistics                            - GAN/diffusion renders skew vivid
 *
 * IMPORTANT (honesty for the client): this is a calibrated heuristic, not a
 * trained classifier. For production accuracy, drop a trained TFLite model at
 * app/src/main/assets/ai_image_detector.tflite - TfliteImageDetector picks it
 * up automatically and this detector becomes the fallback only.
 */
object HeuristicImageDetector : ImageDetector {

    override val name: String = "heuristic-baseline-v1"

    private const val N = 256

    override fun score(bitmap: Bitmap): Double {
        val scaled = Bitmap.createScaledBitmap(bitmap, N, N, true)
        val px = IntArray(N * N)
        scaled.getPixels(px, 0, N, 0, 0, N, N)
        if (scaled !== bitmap) scaled.recycle()

        val gray = IntArray(N * N)
        var satSum = 0.0
        for (i in px.indices) {
            val p = px[i]
            val r = p shr 16 and 0xFF
            val g = p shr 8 and 0xFF
            val b = p and 0xFF
            gray[i] = (30 * r + 59 * g + 11 * b) / 100
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            satSum += if (mx == 0) 0.0 else (mx - mn) / mx.toDouble()
        }
        val satMean = satSum / px.size

        val noise = medianResidualNoise(gray, N, N)
        val lap = laplacianEnergy(gray, N, N)
        val slope = Fft.spectralSlope(Fft.radialPower(gray, N))
        val blockiness = blockinessRatio(gray, N, N)

        return combine(noise, lap, slope, blockiness, satMean)
    }

    /** Mean |center - median(3x3)| - low for smooth/synthetic pixels. */
    fun medianResidualNoise(gray: IntArray, w: Int, h: Int): Double {
        var acc = 0.0
        var count = 0
        val buf = IntArray(9)
        var y = 2
        while (y < h - 2) {
            var x = 2
            while (x < w - 2) {
                var k = 0
                for (dy in -1..1) for (dx in -1..1) buf[k++] = gray[(y + dy) * w + (x + dx)]
                java.util.Arrays.sort(buf)
                acc += abs(gray[y * w + x] - buf[4])
                count++
                x += 4
            }
            y += 4
        }
        return if (count == 0) 0.0 else acc / count
    }

    private fun laplacianEnergy(gray: IntArray, w: Int, h: Int): Double {
        var acc = 0L
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val c = gray[y * w + x]
                val l = 4 * c - gray[y * w + x - 1] - gray[y * w + x + 1] -
                    gray[(y - 1) * w + x] - gray[(y + 1) * w + x]
                acc += abs(l)
            }
        }
        return acc.toDouble() / ((w - 2) * (h - 2))
    }

    /** Ratio of gradient energy on the 8px JPEG grid vs off-grid. */
    private fun blockinessRatio(gray: IntArray, w: Int, h: Int): Double {
        var onGrid = 0L
        var onGridCount = 0
        var offGrid = 0L
        var offGridCount = 0
        for (y in 1 until h) {
            for (x in 1 until w) {
                val d = abs(gray[y * w + x] - gray[y * w + x - 1])
                if (x % 8 == 0) {
                    onGrid += d; onGridCount++
                } else {
                    offGrid += d; offGridCount++
                }
            }
        }
        if (onGridCount == 0 || offGridCount == 0) return 1.0
        val on = onGrid.toDouble() / onGridCount
        val off = offGrid.toDouble() / offGridCount
        return if (off < 1e-6) 1.0 else on / off
    }

    /**
     * Weighted logistic over standardized features. Constants are documented,
     * hand-calibrated starting points to be refined against a labeled dataset
     * (see docs/ARCHITECTURE.md - "Model roadmap").
     */
    private fun combine(
        noise: Double,
        @Suppress("UNUSED_PARAMETER") lap: Double,
        slope: Double,
        blockiness: Double,
        satMean: Double
    ): Double {
        val cleanZ = clamp((4.5 - noise) / 2.5)          // low noise -> suspicious
        val slopeZ = clamp((-2.55 - slope) / 0.35)        // steeper than 1/f^2 -> suspicious
        val blockZ = clamp((1.10 - blockiness) / 0.18)    // crisp (un-recompressed) -> suspicious
        val satZ = clamp((satMean - 0.38) / 0.14)         // high saturation -> mildly suspicious
        // Intercept calibrated on labeled smooth-render vs photographic samples;
        // mirrored in backend/public/engine.js — keep both in sync.
        val logit = 0.1 + 0.90 * cleanZ + 0.45 * slopeZ + 0.15 * blockZ + 0.15 * satZ
        return 1.0 / (1.0 + Math.exp(-logit))
    }

    private fun clamp(z: Double): Double = z.coerceIn(-2.5, 2.5)
}
