package com.aishield.detector.ui

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.aishield.detector.R

/** Shared score -> color/verdict mapping for the app UI. */
object ScoreStyle {

    @ColorRes
    fun colorRes(overall: Double): Int = when {
        overall >= 0.90 -> R.color.score_alert
        overall >= 0.70 -> R.color.score_warn
        else -> R.color.score_ok
    }

    @StringRes
    fun verdict(overall: Double): Int = when {
        overall >= 0.90 -> R.string.verdict_likely_ai
        overall >= 0.70 -> R.string.verdict_possibly_ai
        else -> R.string.verdict_authentic
    }
}
