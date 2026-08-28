package com.aishield.detector.core

import java.util.concurrent.CopyOnWriteArrayList

/** Tiny observable state shared between ScanService and MainActivity. */
object ProtectionState {

    @Volatile
    var isRunning: Boolean = false
        private set

    @Volatile
    var lastAlertAtMs: Long = 0

    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

    fun set(running: Boolean) {
        isRunning = running
        listeners.forEach { it(running) }
    }

    fun onRunningChanged(listener: (Boolean) -> Unit) {
        listeners.add(listener)
    }

    /** Called by UI owners (activities) in onDestroy to avoid leaks. */
    fun clearListeners() {
        listeners.clear()
    }
}
