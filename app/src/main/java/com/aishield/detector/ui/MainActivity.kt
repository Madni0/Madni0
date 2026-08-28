package com.aishield.detector.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.aishield.detector.App
import com.aishield.detector.R
import com.aishield.detector.capture.ScanService
import com.aishield.detector.core.AccountStore
import com.aishield.detector.core.ConfigRepository
import com.aishield.detector.core.ProtectionState
import com.aishield.detector.databinding.ActivityMainBinding
import com.aishield.detector.util.AppNames

/**
 * Home screen: one switch to enable "AI Protection", permission status,
 * and today's stats. Everything else happens silently in ScanService.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var recentAdapter: HistoryAdapter
    private var suppressSwitch = false

    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val db get() = (application as App).db

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                ScanService.start(this, result.resultCode, result.data!!)
            } else {
                setSwitch(false)
                Toast.makeText(this, R.string.toast_consent_needed, Toast.LENGTH_SHORT).show()
            }
        }

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshUi()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recentAdapter = HistoryAdapter { id ->
            startActivity(
                Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, id)
            )
        }
        binding.recyclerRecent.layoutManager = LinearLayoutManager(this)
        binding.recyclerRecent.adapter = recentAdapter

        binding.switchProtection.setOnCheckedChangeListener { _, checked ->
            if (!suppressSwitch) onProtectionToggled(checked)
        }
        binding.btnGrantOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        binding.btnGrantNotif.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.btnGrantUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        ProtectionState.onRunningChanged { running ->
            runOnUiThread { setSwitch(running) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onDestroy() {
        ProtectionState.clearListeners()
        super.onDestroy()
    }

    private fun refreshUi() {
        setSwitch(ProtectionState.isRunning)

        // Permission cards
        val overlayOk = Settings.canDrawOverlays(this)
        binding.tvOverlayStatus.text =
            getString(if (overlayOk) R.string.status_granted else R.string.status_not_granted)
        binding.btnGrantOverlay.visibility = if (overlayOk) View.GONE else View.VISIBLE

        if (Build.VERSION.SDK_INT >= 33) {
            val notifOk = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            binding.cardNotif.visibility = View.VISIBLE
            binding.tvNotifStatus.text =
                getString(if (notifOk) R.string.status_granted else R.string.status_not_granted)
            binding.btnGrantNotif.visibility = if (notifOk) View.GONE else View.VISIBLE
        } else {
            binding.cardNotif.visibility = View.GONE
        }

        val usageOk = AppNames.hasUsageAccess(this)
        binding.tvUsageStatus.text =
            getString(if (usageOk) R.string.status_granted else R.string.status_optional_not_granted)
        binding.btnGrantUsage.visibility = if (usageOk) View.GONE else View.VISIBLE

        // Stats
        val (scanned, alerted) = try {
            db.todayCounts()
        } catch (_: Throwable) {
            0 to 0
        }
        binding.tvScanned.text = scanned.toString()
        binding.tvAlerted.text = alerted.toString()

        // Recent alerts
        recentAdapter.submit(try {
            db.recentAlerts(5)
        } catch (_: Throwable) {
            emptyList()
        })
        binding.tvEmptyRecent.visibility =
            if (recentAdapter.isEmpty()) View.VISIBLE else View.GONE

        // Account + backend caption
        val account = AccountStore.current()
        binding.tvAccount.text = account?.let {
            getString(if (it.guest) R.string.guest_session else R.string.signed_in_as, it.email)
        } ?: getString(R.string.not_signed_in)
        binding.tvBackend.text =
            getString(R.string.backend_caption, ConfigRepository.get().backendUrl)
    }

    private fun onProtectionToggled(checked: Boolean) {
        if (checked) {
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else {
            stopService(Intent(this, ScanService::class.java))
            refreshUi()
        }
    }

    private fun setSwitch(checked: Boolean) {
        suppressSwitch = true
        binding.switchProtection.isChecked = checked
        suppressSwitch = false
        binding.tvProtectionState.text = getString(
            if (checked) R.string.protection_on else R.string.protection_off
        )
        binding.tvProtectionHint.text = getString(
            if (checked) R.string.protection_hint_on else R.string.protection_hint_off
        )
    }
}
