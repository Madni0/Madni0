package com.aishield.detector.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.aishield.detector.core.AccountStore
import com.aishield.detector.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AccountStore.isSignedIn()) {
            goMain()
            return
        }
        if (getSharedPreferences("aishield_prefs", Context.MODE_PRIVATE)
                .getBoolean("onboarded", false)
        ) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGetStarted.setOnClickListener {
            getSharedPreferences("aishield_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("onboarded", true).apply()
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
        }
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
