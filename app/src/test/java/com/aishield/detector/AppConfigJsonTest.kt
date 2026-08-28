package com.aishield.detector

import com.aishield.detector.core.AppConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConfigJsonTest {

    @Test
    fun `parses full server payload`() {
        val json = JSONObject(
            """
            {
              "alertThreshold": 0.92,
              "logThreshold": 0.75,
              "sampleIntervalMs": 3000,
              "overlayPosition": "bottom",
              "sessionTimeoutMin": 30,
              "audioEnabled": false
            }
            """.trimIndent()
        )
        val cfg = AppConfig.fromJson(json)
        assertEquals(0.92, cfg.alertThreshold, 1e-9)
        assertEquals(0.75, cfg.logThreshold, 1e-9)
        assertEquals(3000L, cfg.sampleIntervalMs)
        assertEquals("bottom", cfg.overlayPosition)
        assertEquals(30, cfg.sessionTimeoutMin)
        assertEquals(false, cfg.audioEnabled)
    }

    @Test
    fun `falls back per-field on partial payload`() {
        val cfg = AppConfig.fromJson(JSONObject("""{"alertThreshold": 0.95}"""))
        assertEquals(0.95, cfg.alertThreshold, 1e-9)
        assertEquals(AppConfig.DEFAULT.logThreshold, cfg.logThreshold, 1e-9)
        assertEquals(AppConfig.DEFAULT.sampleIntervalMs, cfg.sampleIntervalMs)
    }

    @Test
    fun `out-of-range values are coerced`() {
        val cfg = AppConfig.fromJson(
            JSONObject("""{"alertThreshold": 5.0, "sampleIntervalMs": 1}""")
        )
        assertTrue(cfg.alertThreshold <= 1.0)
        assertTrue(cfg.sampleIntervalMs >= 300)
    }

    @Test
    fun `round trip toJson-fromJson is stable`() {
        val cfg = AppConfig.DEFAULT.copy(overlayPosition = "bottom")
        val parsed = AppConfig.fromJson(cfg.toJson())
        assertEquals(cfg, parsed)
    }
}
