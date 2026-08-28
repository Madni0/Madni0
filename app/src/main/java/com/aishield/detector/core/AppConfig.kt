package com.aishield.detector.core

import org.json.JSONObject

/**
 * Runtime configuration.
 *
 * Detection/alert thresholds are SERVER-CONTROLLED (client requirement):
 * the app fetches them from the config backend (see /backend) and caches
 * them locally. Defaults below are used until the first successful fetch
 * or while offline.
 */
data class AppConfig(
    /** >= this overall probability (0..1) -> show the on-screen AI warning. */
    val alertThreshold: Double = 0.90,
    /** >= this probability -> silently record internally, no UI (0.70..alertThreshold). */
    val logThreshold: Double = 0.70,
    /** Minimum ms between two analyses while the user scrolls. */
    val sampleIntervalMs: Long = 2500,
    /** Mean-abs-diff (0..1) on a downscaled gray frame considered "new content". */
    val changeThreshold: Double = 0.035,
    /** Perceptual-hash hamming distance below which content is treated as duplicate. */
    val dedupHamming: Int = 10,
    /** Auto-hide the overlay chip after this many ms. */
    val overlayAutoDismissMs: Long = 8000,
    /** "top" or "bottom" screen edge for the overlay chip. */
    val overlayPosition: String = "top",
    /** Auto-stop protection after N minutes; 0 = until user stops. */
    val sessionTimeoutMin: Int = 0,
    /** Max width of the bitmap passed to detectors (performance guard). */
    val analysisMaxWidth: Int = 512,
    /** Whether playback audio is analyzed for synthetic voice. */
    val audioEnabled: Boolean = true,
    /** Config/logging backend base URL. */
    val backendUrl: String = "http://10.0.2.2:3000"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("alertThreshold", alertThreshold)
        put("logThreshold", logThreshold)
        put("sampleIntervalMs", sampleIntervalMs)
        put("changeThreshold", changeThreshold)
        put("dedupHamming", dedupHamming)
        put("overlayAutoDismissMs", overlayAutoDismissMs)
        put("overlayPosition", overlayPosition)
        put("sessionTimeoutMin", sessionTimeoutMin)
        put("analysisMaxWidth", analysisMaxWidth)
        put("audioEnabled", audioEnabled)
        put("backendUrl", backendUrl)
    }

    companion object {
        val DEFAULT = AppConfig()

        /** Merges a JSON object over a base config, falling back per-field. */
        fun fromJson(o: JSONObject, base: AppConfig = DEFAULT): AppConfig = AppConfig(
            alertThreshold = o.optDouble("alertThreshold", base.alertThreshold).coerceIn(0.0, 1.0),
            logThreshold = o.optDouble("logThreshold", base.logThreshold).coerceIn(0.0, 1.0),
            sampleIntervalMs = o.optLong("sampleIntervalMs", base.sampleIntervalMs).coerceIn(300, 60_000),
            changeThreshold = o.optDouble("changeThreshold", base.changeThreshold).coerceIn(0.005, 0.5),
            dedupHamming = o.optInt("dedupHamming", base.dedupHamming).coerceIn(0, 64),
            overlayAutoDismissMs = o.optLong("overlayAutoDismissMs", base.overlayAutoDismissMs).coerceIn(1500, 60_000),
            overlayPosition = o.optString("overlayPosition", base.overlayPosition),
            sessionTimeoutMin = o.optInt("sessionTimeoutMin", base.sessionTimeoutMin).coerceIn(0, 720),
            analysisMaxWidth = o.optInt("analysisMaxWidth", base.analysisMaxWidth).coerceIn(128, 1280),
            audioEnabled = o.optBoolean("audioEnabled", base.audioEnabled),
            backendUrl = o.optString("backendUrl", base.backendUrl)
        )
    }
}
