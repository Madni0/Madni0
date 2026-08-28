package com.aishield.detector

import com.aishield.detector.core.AppConfig
import com.aishield.detector.core.ThresholdEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ThresholdEngineTest {

    private val cfg = AppConfig.DEFAULT // log 0.70, alert 0.90

    @Test
    fun `below log threshold - do nothing`() {
        assertEquals(ThresholdEngine.Action.IGNORE, ThresholdEngine.decide(0.00, cfg))
        assertEquals(ThresholdEngine.Action.IGNORE, ThresholdEngine.decide(0.69, cfg))
    }

    @Test
    fun `mid band - record silently without UI`() {
        assertEquals(ThresholdEngine.Action.LOG_SILENT, ThresholdEngine.decide(0.70, cfg))
        assertEquals(ThresholdEngine.Action.LOG_SILENT, ThresholdEngine.decide(0.89, cfg))
    }

    @Test
    fun `alert band - show warning`() {
        assertEquals(ThresholdEngine.Action.ALERT, ThresholdEngine.decide(0.90, cfg))
        assertEquals(ThresholdEngine.Action.ALERT, ThresholdEngine.decide(0.94, cfg))
        assertEquals(ThresholdEngine.Action.ALERT, ThresholdEngine.decide(1.00, cfg))
    }

    @Test
    fun `custom backend thresholds are honored`() {
        val custom = cfg.copy(alertThreshold = 0.80, logThreshold = 0.50)
        assertEquals(ThresholdEngine.Action.IGNORE, ThresholdEngine.decide(0.49, custom))
        assertEquals(ThresholdEngine.Action.LOG_SILENT, ThresholdEngine.decide(0.50, custom))
        assertEquals(ThresholdEngine.Action.ALERT, ThresholdEngine.decide(0.80, custom))
    }
}
