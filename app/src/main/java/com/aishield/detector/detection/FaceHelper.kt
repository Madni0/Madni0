package com.aishield.detector.detection

import android.graphics.Bitmap
import android.media.FaceDetector

/**
 * Dependency-free face localization via the platform FaceDetector
 * (works on RGB_565 bitmaps). Used to feed the deepfake analyzer.
 */
object FaceHelper {

    data class Face(val cx: Float, val cy: Float, val eyesDistance: Float)

    fun findFaces(src: Bitmap, maxFaces: Int = 3): List<Face> {
        val maxW = 320
        val scale = maxW.toFloat() / src.width
        val w = Math.max(2, Math.round(src.width * scale) and 1.inv()) // FaceDetector needs even width
        val h = Math.max(2, Math.round(src.height * scale))
        if (w < 16 || h < 16) return emptyList()

        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        val rgb565 = if (scaled.config == Bitmap.Config.RGB_565) scaled
        else scaled.copy(Bitmap.Config.RGB_565, false)
        if (rgb565 !== scaled) scaled.recycle()

        return try {
            val detector = FaceDetector(w, h, maxFaces)
            val faces = Array(maxFaces) { FaceDetector.Face() }
            val found = detector.findFaces(rgb565, faces)
            val inv = 1f / scale
            (0 until found).mapNotNull {
                val f = faces.getOrNull(it) ?: return@mapNotNull null
                Face(
                    cx = f.position().x * inv,
                    cy = f.position().y * inv,
                    eyesDistance = f.eyesDistance() * inv
                )
            }
        } catch (_: Throwable) {
            emptyList()
        } finally {
            rgb565.recycle()
        }
    }
}
