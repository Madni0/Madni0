package com.aishield.detector.core

/**
 * Fuses component probabilities into one overall AI-likelihood score.
 *
 * The visual signal dominates; deepfake and synthetic-audio signals
 * refine the score when they were actually analyzed (non-null).
 */
object ScoreFusion {

    // Documented, fixed weights (unit-tested).
    private const val W_VISUAL = 0.70
    private const val W_DEEPFAKE = 0.15
    private const val W_AUDIO = 0.15

    fun fuse(visual: Double, deepfake: Double?, audio: Double?): Double {
        val v = visual.coerceIn(0.0, 1.0)
        val d = deepfake?.coerceIn(0.0, 1.0)
        val a = audio?.coerceIn(0.0, 1.0)

        val sum = W_VISUAL +
            (if (d != null) W_DEEPFAKE else 0.0) +
            (if (a != null) W_AUDIO else 0.0)

        var acc = v * W_VISUAL
        if (d != null) acc += d * W_DEEPFAKE
        if (a != null) acc += a * W_AUDIO
        return (acc / sum).coerceIn(0.0, 1.0)
    }
}
