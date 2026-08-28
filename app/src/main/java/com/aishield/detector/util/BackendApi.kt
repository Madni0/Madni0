package com.aishield.detector.util

import com.aishield.detector.core.ConfigRepository
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thin HTTP client for the config/logging backend (see /backend).
 * All calls are synchronous; call from a worker thread.
 */
object BackendApi {

    data class AuthUser(val token: String, val email: String, val guest: Boolean)

    private fun post(path: String, body: JSONObject): JSONObject {
        val url = URL(ConfigRepository.get().backendUrl.trimEnd('/') + path)
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                it.write(body.toString())
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code: $text")
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun requireString(o: JSONObject, key: String): String =
        o.optString(key, "").ifEmpty { throw IllegalStateException("Missing $key") }

    fun guest(): Result<AuthUser> = runCatching {
        val o = post("/auth/guest", JSONObject())
        AuthUser(requireString(o, "token"), o.optString("email", "Guest"), guest = true)
    }

    fun register(email: String, password: String): Result<AuthUser> = runCatching {
        val o = post(
            "/auth/register",
            JSONObject().put("email", email).put("password", password)
        )
        AuthUser(requireString(o, "token"), o.optString("email", email), guest = false)
    }

    fun login(email: String, password: String): Result<AuthUser> = runCatching {
        val o = post(
            "/auth/login",
            JSONObject().put("email", email).put("password", password)
        )
        AuthUser(requireString(o, "token"), o.optString("email", email), guest = false)
    }

    /** Fire-and-forget logging of detections to the backend. */
    fun postLog(rowJson: JSONObject) {
        runCatching { post("/logs", rowJson) }
    }
}
