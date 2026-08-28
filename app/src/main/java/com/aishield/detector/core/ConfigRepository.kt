package com.aishield.detector.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loads the app configuration: defaults <- server JSON (cached) <- local overrides.
 *
 * Thresholds (alert/log) always come from the server config so they can be
 * tuned from the backend without an app update (client requirement).
 */
object ConfigRepository {

    private const val PREFS = "aishield_prefs"
    private const val KEY_SERVER_JSON = "server_config_json"
    private const val KEY_BACKEND_URL = "backend_url"
    private const val KEY_SESSION_MIN = "session_minutes" // -1 = follow server/default
    private const val KEY_OVERLAY_POS = "overlay_pos"     // "" = follow default
    private const val KEY_AUDIO = "audio_enabled"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Merged, effective configuration. Safe to call from any thread. */
    fun get(): AppConfig {
        var cfg = AppConfig.DEFAULT
        prefs.getString(KEY_SERVER_JSON, null)?.let { raw ->
            runCatching { cfg = AppConfig.fromJson(JSONObject(raw)) }
        }
        val urlOverride = prefs.getString(KEY_BACKEND_URL, null)
        val sessionOverride = prefs.getInt(KEY_SESSION_MIN, -1)
        val posOverride = prefs.getString(KEY_OVERLAY_POS, null)
        return cfg.copy(
            backendUrl = urlOverride ?: cfg.backendUrl,
            sessionTimeoutMin = if (sessionOverride >= 0) sessionOverride else cfg.sessionTimeoutMin,
            overlayPosition = posOverride?.takeIf { it.isNotEmpty() } ?: cfg.overlayPosition,
            audioEnabled = prefs.getBoolean(KEY_AUDIO, cfg.audioEnabled)
        )
    }

    /** Fetches /config from the backend and caches it. Calls back on a worker thread. */
    fun refreshAsync(onDone: (AppConfig, ok: Boolean) -> Unit = { _, _ -> }) {
        Thread {
            var ok = false
            try {
                val url = URL(get().backendUrl.trimEnd('/') + "/config")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                // Validate before caching.
                AppConfig.fromJson(JSONObject(body))
                prefs.edit().putString(KEY_SERVER_JSON, body).apply()
                ok = true
            } catch (_: Exception) {
            }
            onDone(get(), ok)
        }.start()
    }

    // ---- local overrides (UX-level settings) ----

    fun setBackendUrl(url: String) = prefs.edit().putString(KEY_BACKEND_URL, url.trim()).apply()
    fun backendUrlOverride(): String? = prefs.getString(KEY_BACKEND_URL, null)

    fun setSessionMinutes(minutes: Int) = prefs.edit().putInt(KEY_SESSION_MIN, minutes).apply()
    fun sessionMinutesOverride(): Int = prefs.getInt(KEY_SESSION_MIN, -1)

    fun setOverlayPosition(pos: String) = prefs.edit().putString(KEY_OVERLAY_POS, pos).apply()
    fun overlayPositionOverride(): String? = prefs.getString(KEY_OVERLAY_POS, null)

    fun setAudioEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUDIO, enabled).apply()
}
