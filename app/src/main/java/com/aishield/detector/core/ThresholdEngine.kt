package com.aishield.detector.core

/**
 * Maps a fused AI-probability score to a user-facing action.
 *
 * Client spec:
 *   0 .. logThreshold (default 0.69)  -> do nothing
 *   logThreshold .. alertThreshold    -> record internally, no UI
 *   >= alertThreshold (default 0.90)  -> show the AI warning overlay
 */
object ThresholdEngine {

    enum class Action { IGNORE, LOG_SILENT, ALERT }

    fun decide(overall: Double, cfg: AppConfig): Action = when {
        overall >= cfg.alertThreshold -> Action.ALERT
        overall >= cfg.logThreshold -> Action.LOG_SILENT
        else -> Action.IGNORE
    }
}
