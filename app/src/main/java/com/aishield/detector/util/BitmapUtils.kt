package com.aishield.detector.util

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import java.io.File

object BitmapUtils {

    /** Luminance array (row-major, 0..255) scaled to targetW x targetH. */
    fun downscaleGray(src: Bitmap, targetW: Int, targetH: Int): IntArray {
        val scaled = Bitmap.createScaledBitmap(src, targetW, targetH, true)
        val px = IntArray(targetW * targetH)
        scaled.getPixels(px, 0, targetW, 0, 0, targetW, targetH)
        if (scaled !== src) scaled.recycle()
        val gray = IntArray(px.size)
        for (i in px.indices) {
            val p = px[i]
            gray[i] = (30 * (p shr 16 and 0xFF) + 59 * (p shr 8 and 0xFF) + 11 * (p and 0xFF)) / 100
        }
        return gray
    }

    /** Mean absolute difference between two same-size gray buffers, normalized 0..1. */
    fun meanAbsDiff(a: IntArray, b: IntArray): Double {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0.0
        var acc = 0L
        for (i in 0 until n) acc += Math.abs(a[i] - b[i])
        return (acc.toDouble() / n) / 255.0
    }

    /** Scales so width <= maxWidth, preserving aspect. Returns a new bitmap. */
    fun scaledToMaxWidth(src: Bitmap, maxWidth: Int): Bitmap {
        if (src.width <= maxWidth) return src
        val ratio = maxWidth.toDouble() / src.width
        val h = Math.max(1, Math.round(src.height * ratio).toInt())
        return Bitmap.createScaledBitmap(src, maxWidth, h, true)
    }

    /**
     * Converts an ImageReader RGBA frame to a Bitmap, honoring row stride.
     * Caller owns the returned bitmap and must recycle it.
     */
    fun fromImage(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        val rowPadding = rowStride - width * pixelStride

        val paddedBitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        buffer.rewind()
        paddedBitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(paddedBitmap, 0, 0, width, height).also {
            paddedBitmap.recycle()
        }
    }

    /** Saves a small JPEG thumbnail under dir; returns absolute path. */
    fun saveThumbnail(src: Bitmap, dir: File, id: Long): String? = runCatching {
        val small = scaledToMaxWidth(src, 256)
        val out = File(dir, "thumb_$id.jpg")
        out.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        if (small !== src) small.recycle()
        out.absolutePath
    }.getOrNull()

    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
