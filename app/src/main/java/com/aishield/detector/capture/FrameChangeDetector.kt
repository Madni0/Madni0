package com.aishield.detector.capture

import android.graphics.Bitmap
import com.aishield.detector.util.BitmapUtils

/**
 * Cheap scroll/content-change detection: compares a 64x48 grayscale
 * thumbnail of the current frame against the previous one. Only when the
 * screen has "moved enough" do we schedule a full analysis - this is what
 * keeps the pipeline silent, cool and battery-friendly.
 */
class FrameChangeDetector(private val threshold: Double) {

    private var last: IntArray? = null

    fun changed(bitmap: Bitmap): Boolean {
        val gray = BitmapUtils.downscaleGray(bitmap, 64, 48)
        val prev = last
        last = gray
        return prev?.let { BitmapUtils.meanAbsDiff(gray, it) > threshold } ?: false
    }

    fun reset() {
        last = null
    }
}
