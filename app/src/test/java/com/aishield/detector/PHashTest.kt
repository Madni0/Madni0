package com.aishield.detector

import com.aishield.detector.core.PHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PHashTest {

    private fun flat(v: Int, w: Int = 32, h: Int = 32) = IntArray(w * h) { v }

    @Test
    fun `identical images have distance 0`() {
        val a = flat(120)
        assertEquals(0, PHash.hamming(PHash.dHash(a, 32, 32), PHash.dHash(a, 32, 32)))
    }

    @Test
    fun `similar images stay close`() {
        // uniform gray shifted by a tiny amount
        val a = PHash.dHash(flat(120), 32, 32)
        val b = PHash.dHash(flat(124), 32, 32)
        assertTrue(PHash.hamming(a, b) <= 4)
    }

    @Test
    fun `gradient flip differs strongly`() {
        // dHash compares horizontal neighbors, so mirror left-to-right.
        val ltr = IntArray(32 * 32) { (it % 32) * 8 }        // dark -> light, left to right
        val rtl = IntArray(32 * 32) { (31 - it % 32) * 8 }   // light -> dark
        assertTrue(PHash.hamming(PHash.dHash(ltr, 32, 32), PHash.dHash(rtl, 32, 32)) >= 48)
    }

    @Test
    fun `hamming counts differing bits`() {
        assertEquals(0, PHash.hamming(0b1010L, 0b1010L))
        assertEquals(4, PHash.hamming(0b0000L, 0b1111L))
        assertEquals(1, PHash.hamming(0L, 1L))
    }
}
