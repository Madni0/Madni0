package com.aishield.detector.ui

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aishield.detector.R
import com.aishield.detector.core.AppConfig
import com.aishield.detector.core.ConfigRepository
import com.aishield.detector.databinding.ActivitySettingsBinding
import kotlin.math.roundToInt

/**
 * Settings. Detection thresholds intentionally have NO local edit field:
 * they are server-controlled (client requirement). This screen shows the
 * live values and lets the user point the app at a different backend.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val positions =
        arrayOf("top", "bottom")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_settings)

        val cfg = ConfigRepository.get()
        binding.etBackendUrl.text = ConfigRepository.backendUrlOverride() ?: cfg.backendUrl
        binding.etSessionMinutes.text =
            ConfigRepository.sessionMinutesOverride().takeIf { it >= 0 }?.toString() ?: ""
        binding.switchAudio.isChecked = cfg.audioEnabled

        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            arrayOf(
                getString(R.string.overlay_top),
                getString(R.string.overlay_bottom)
            )
        )
        binding.spinnerOverlayPos.adapter = adapter
        val currentIdx = if (cfg.overlayPosition == "bottom") 1 else 0
        binding.spinnerOverlayPos.setSelection(currentIdx)

        binding.btnRefreshConfig.setOnClickListener {
            binding.progressConfig.visibility = View.VISIBLE
            ConfigRepository.refreshAsync { updated, ok ->
                runOnUiThread {
                    binding.progressConfig.visibility = View.GONE
                    showThresholds(updated)
                    Toast.makeText(
                        this,
                        getString(if (ok) R.string.config_refresh_ok else R.string.config_refresh_fail),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        showThresholds(cfg)

        binding.btnSave.setOnClickListener {
            ConfigRepository.setBackendUrl(
                binding.etBackendUrl.text?.toString()?.trim().orEmpty()
                    .ifEmpty { AppConfig.DEFAULT.backendUrl }
            )
            val minutes = binding.etSessionMinutes.text?.toString()?.trim()?.toIntOrNull() ?: 0
            ConfigRepository.setSessionMinutes(minutes.coerceIn(0, 720))
            ConfigRepository.setOverlayPosition(positions[binding.spinnerOverlayPos.selectedItemPosition])
            ConfigRepository.setAudioEnabled(binding.switchAudio.isChecked)
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun showThresholds(cfg: AppConfig) {
        binding.tvThresholds.text = getString(
            R.string.thresholds_summary,
            (cfg.alertThreshold * 100).roundToInt(),
            (cfg.logThreshold * 100).roundToInt(),
            cfg.sampleIntervalMs / 1000.0
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
