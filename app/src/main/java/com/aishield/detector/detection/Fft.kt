package com.aishield.detector.detection

/** Compact iterative radix-2 FFT used by the frequency-domain features. */
object Fft {

    /** In-place complex FFT. Length must be a power of two. */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        if (n and (n - 1) != 0) throw IllegalArgumentException("FFT size must be power of two")
        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        // Butterflies
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wRe = Math.cos(ang)
            val wIm = Math.sin(ang)
            var i = 0
            while (i < n) {
                var curRe = 1.0
                var curIm = 0.0
                val half = len shr 1
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vRe = re[b] * curRe - im[b] * curIm
                    val vIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - vRe
                    im[b] = im[a] - vIm
                    re[a] += vRe
                    im[a] += vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Radially-averaged power spectrum of an n x n gray image (n power of two).
     * Returns mean power per radial bin, bin 0 = DC, size n/2.
     */
    fun radialPower(gray: IntArray, n: Int): DoubleArray {
        val re = DoubleArray(n * n)
        val im = DoubleArray(n * n)
        for (i in 0 until n * n) re[i] = gray[i].toDouble()

        val rowRe = DoubleArray(n)
        val rowIm = DoubleArray(n)
        for (y in 0 until n) {
            for (x in 0 until n) {
                rowRe[x] = re[y * n + x]
                rowIm[x] = 0.0
            }
            fft(rowRe, rowIm)
            for (x in 0 until n) {
                re[y * n + x] = rowRe[x]
                im[y * n + x] = rowIm[x]
            }
        }
        val colRe = DoubleArray(n)
        val colIm = DoubleArray(n)
        for (x in 0 until n) {
            for (y in 0 until n) {
                colRe[y] = re[y * n + x]
                colIm[y] = im[y * n + x]
            }
            fft(colRe, colIm)
            for (y in 0 until n) {
                re[y * n + x] = colRe[y]
                im[y * n + x] = colIm[y]
            }
        }

        val half = n / 2
        val power = DoubleArray(half)
        val counts = IntArray(half)
        for (y in 0 until n) {
            for (x in 0 until n) {
                val fy = if (y < half) y else y - n
                val fx = if (x < half) x else x - n
                val r = Math.sqrt((fy * fy + fx * fx).toDouble())
                val bin = Math.min(half - 1, r.toInt())
                power[bin] += re[y * n + x] * re[y * n + x] + im[y * n + x] * im[y * n + x]
                counts[bin]++
            }
        }
        for (b in 0 until half) if (counts[b] > 0) power[b] /= counts[b]
        return power
    }

    /** Log-log linear-regression slope of a power spectrum over bins [1, upTo). */
    fun spectralSlope(power: DoubleArray, upTo: Int = power.size / 2): Double {
        var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        var count = 0
        for (b in 1 until upTo.coerceAtMost(power.size)) {
            val x = Math.log(b.toDouble())
            val y = Math.log(power[b] + 1e-12)
            sx += x; sy += y; sxx += x * x; sxy += x * y
            count++
        }
        if (count == 0) return 0.0
        val denom = count * sxx - sx * sx
        if (Math.abs(denom) < 1e-12) return 0.0
        return (count * sxy - sx * sy) / denom
    }
}
