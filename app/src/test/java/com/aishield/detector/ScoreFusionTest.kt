package com.aishield.detector

import com.aishield.detector.core.ScoreFusion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreFusionTest {

    @Test
    fun `visual only equals visual`() {
        assertEquals(0.83, ScoreFusion.fuse(0.83, null, null), 1e-9)
    }

    @Test
    fun `all components weighted correctly`() {
        val expected = (0.96 * 0.70 + 0.82 * 0.15 + 0.71 * 0.15) / 1.0
        assertEquals(expected, ScoreFusion.fuse(0.96, 0.82, 0.71), 1e-9)
    }

    @Test
    fun `visual and deepfake only`() {
        val expected = (0.96 * 0.70 + 0.82 * 0.15) / 0.85
        assertEquals(expected, ScoreFusion.fuse(0.96, 0.82, null), 1e-9)
    }

    @Test
    fun `scores stay within 0-1`() {
        assertTrue(ScoreFusion.fuse(1.0, 1.0, 1.0) <= 1.0)
        assertTrue(ScoreFusion.fuse(0.0, 0.0, 0.0) >= 0.0)
    }
}
