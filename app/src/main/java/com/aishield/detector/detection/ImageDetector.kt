package com.aishield.detector.detection

import android.graphics.Bitmap

/** Common interface for image AI-likelihood detectors. */
interface ImageDetector {
    val name: String

    /** @return probability in 0..1 that the image is AI-generated/manipulated. */
    fun score(bitmap: Bitmap): Double
}
