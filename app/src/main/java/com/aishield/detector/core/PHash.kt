package com.aishield.detector.core

/**
 * Difference hash (dHash) over a grayscale buffer, used to skip analyzing
 * content that was already seen while the user scrolls (dedup).
 *
 * Pure-array implementation so it is unit-testable without Android classes.
 */
object PHash {

    private const val GRID_W = 9
    private const val GRID_H = 8

    /**
     * @param gray grayscale pixels, row-major, values 0..255
     * @param w width of the gray buffer
     * @param h height of the gray buffer
     * @return 64-bit hash
     */
    fun dHash(gray: IntArray, w: Int, h: Int): Long {
        var hash = 0L
        for (gy in 0 until GRID_H) {
            for (gx in 0 until GRID_W - 1) {
                val sx = gx * (w - 1) / (GRID_W - 1)
                val sy = gy * (h - 1) / (GRID_H - 1)
                val left = gray[sy * w + sx]
                val right = gray[sy * w + (sx + 1).coerceAtMost(w - 1)]
                hash = hash shl 1
                if (left > right) hash = hash or 1L
            }
        }
        return hash
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)
}
