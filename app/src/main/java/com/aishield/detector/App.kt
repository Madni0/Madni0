package com.aishield.detector

import android.app.Application
import com.aishield.detector.core.AccountStore
import com.aishield.detector.core.ConfigRepository
import com.aishield.detector.core.DetectionDb

class App : Application() {

    lateinit var db: DetectionDb
        private set

    override fun onCreate() {
        super.onCreate()
        ConfigRepository.init(this)
        AccountStore.init(this)
        db = DetectionDb(this)
    }
}
