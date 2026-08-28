package com.aishield.detector.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.aishield.detector.App
import com.aishield.detector.R
import com.aishield.detector.core.AppConfig
import com.aishield.detector.core.ConfigRepository
import com.aishield.detector.core.DetectionDb
import com.aishield.detector.core.PHash
import com.aishield.detector.core.ProtectionState
import com.aishield.detector.core.ThresholdEngine
import com.aishield.detector.detection.AudioAnalyzer
import com.aishield.detector.detection.DetectionEngine
import com.aishield.detector.overlay.OverlayManager
import com.aishield.detector.ui.DetailActivity
import com.aishield.detector.ui.MainActivity
import com.aishield.detector.util.AppNames
import com.aishield.detector.util.BackendApi
import com.aishield.detector.util.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The "AI Protection" engine: a media-projection foreground service that
 * mirrors the screen, detects new content while the user scrolls, samples it
 * silently and only ever surfaces a warning when the alert threshold is hit.
 *
 * There is deliberately NO "Scanning..." UI anywhere (client requirement).
 */
class ScanService : Service() {

    companion object {
        const val ACTION_STOP = "com.aishield.detector.action.STOP"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_DATA = "data"
        private const val CHANNEL_PROTECTION = "aishield_protection"
        private const val CHANNEL_ALERTS = "aishield_alerts"
        private const val NOTIF_ID = 42
        private const val ALERT_NOTIF_ID = 43

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScanService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null

    private var engine: DetectionEngine? = null
    private var overlay: OverlayManager? = null
    private var audio: AudioAnalyzer? = null
    private var sessionJob: Job? = null

    private var frameMonitor = FrameChangeDetector(AppConfig.DEFAULT.changeThreshold)
    private var lastFrameCheck = 0L
    private var lastAnalysisAt = 0L
    private var lastHash = -1L

    private val db: DetectionDb
        get() = (applicationContext as App).db

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post { stopSelf() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = DetectionEngine(this)
        overlay = OverlayManager(this)
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val data: Intent? = intent?.let {
            IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java)
        }
        if (resultCode == Int.MIN_VALUE || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val pm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = try {
            pm.getMediaProjection(resultCode, data)
        } catch (_: Throwable) {
            stopSelf()
            return START_NOT_STICKY
        }
        projection = proj
        proj.registerCallback(projectionCallback, mainHandler)

        val type = if (Build.VERSION.SDK_INT >= 30) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        ProtectionState.set(true)

        val cfg = ConfigRepository.get()
        frameMonitor = FrameChangeDetector(cfg.changeThreshold)
        lastHash = -1

        if (cfg.audioEnabled && Build.VERSION.SDK_INT >= 29) {
            audio = AudioAnalyzer(proj).also { it.start() }
        }
        if (cfg.sessionTimeoutMin > 0) {
            sessionJob = scope.launch {
                delay(cfg.sessionTimeoutMin * 60_000L)
                stopSelf()
            }
        }
        setupCapture(proj)
        return START_NOT_STICKY
    }

    // ---------------------------------------------------------------- capture

    private fun setupCapture(proj: MediaProjection) {
        val metrics = resources.displayMetrics
        val maxW = 720
        val ratio = min(1.0, maxW.toDouble() / max(1, metrics.widthPixels))
        val w = max(2, (metrics.widthPixels * ratio).roundToInt())
        val h = max(2, (metrics.heightPixels * ratio).roundToInt())

        handlerThread = HandlerThread("aishield-capture").apply { start() }
        val handler = Handler(handlerThread!!.looper)

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3)
        imageReader = reader
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            val bmp = try {
                BitmapUtils.fromImage(image)
            } catch (_: Throwable) {
                null
            } finally {
                image.close()
            }
            if (bmp != null) handleFrame(bmp)
        }, handler)

        virtualDisplay = proj.createVirtualDisplay(
            "aishield-capture", w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler
        )
    }

    private fun handleFrame(frame: android.graphics.Bitmap) {
        val now = SystemClock.uptimeMillis()
        // Cheap change detection throttled to ~4 fps.
        if (now - lastFrameCheck < 250) {
            frame.recycle()
            return
        }
        lastFrameCheck = now
        val changed = try {
            frameMonitor.changed(frame)
        } catch (_: Throwable) {
            true
        }
        val cfg = ConfigRepository.get()
        if (!changed || now - lastAnalysisAt < cfg.sampleIntervalMs || !busy.compareAndSet(false, true)) {
            frame.recycle()
            return
        }
        lastAnalysisAt = now
        scope.launch {
            try {
                analyzeFrame(frame, cfg)
            } catch (_: Throwable) {
                // Never crash, never nag - stay silent.
            } finally {
                busy.set(false)
            }
        }
    }

    private fun analyzeFrame(frame: android.graphics.Bitmap, cfg: AppConfig) {
        val analysis = BitmapUtils.scaledToMaxWidth(frame, cfg.analysisMaxWidth)
        if (analysis !== frame) frame.recycle()
        try {
            val pkg = AppNames.latestForeground(this) ?: "unknown"
            if (pkg == packageName) return // never analyze our own UI

            // Perceptual-hash dedup: skip content we just analyzed.
            val gray = BitmapUtils.downscaleGray(analysis, 64, 48)
            val hash = PHash.dHash(gray, 64, 48)
            if (lastHash != -1L && PHash.hamming(hash, lastHash) < cfg.dedupHamming) return
            lastHash = hash

            val eng = engine ?: return
            val result = eng.analyze(analysis, pkg, audio?.snapshot())
            val action = ThresholdEngine.decide(result.overall, cfg)

            var thumbPath: String? = null
            if (action == ThresholdEngine.Action.ALERT) {
                val dir = File(filesDir, "thumbs").apply { mkdirs() }
                thumbPath = BitmapUtils.saveThumbnail(analysis, dir, System.currentTimeMillis())
            }

            val row = DetectionDb.DetectionRow(
                id = 0,
                packageName = pkg,
                timeMs = System.currentTimeMillis(),
                visual = result.visual,
                deepfake = result.deepfake,
                audio = result.audio,
                c2pa = result.c2pa.name,
                overall = result.overall,
                action = action.name,
                thumbPath = thumbPath
            )
            val id = db.insert(row)

            if (action == ThresholdEngine.Action.ALERT) {
                ProtectionState.lastAlertAtMs = row.timeMs
                val pct = Math.round(result.overall * 100).toInt()
                val shown = overlay?.show(id, pct, cfg) ?: false
                if (!shown) notifyAlert(pct, pkg, id)
                postLogAsync(row.copy(id = id))
            }
        } finally {
            analysis.recycle()
        }
    }

    // ---------------------------------------------------------------- output

    private fun notifyAlert(pct: Int, pkg: String, id: Long) {
        // Fallback when the overlay permission is missing: use a notification.
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.areNotificationsEnabled()) return
        val pi = PendingIntent.getActivity(
            this, (id % 1000).toInt() + 10,
            Intent(this, DetailActivity::class.java).putExtra(DetailActivity.EXTRA_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("Likely AI content - $pct%")
            .setContentText("Detected in ${AppNames.pretty(pkg)}. Tap for the analysis.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ALERT_NOTIF_ID, n) }
    }

    private fun postLogAsync(row: DetectionDb.DetectionRow) {
        scope.launch(Dispatchers.IO) {
            BackendApi.postLog(
                JSONObject().apply {
                    put("token", com.aishield.detector.core.AccountStore.current()?.token ?: JSONObject.NULL)
                    put("packageName", row.packageName)
                    put("visual", row.visual)
                    put("deepfake", row.deepfake ?: JSONObject.NULL)
                    put("audio", row.audio ?: JSONObject.NULL)
                    put("c2pa", row.c2pa)
                    put("overall", row.overall)
                    put("action", row.action)
                    put("timeMs", row.timeMs)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val contentPi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getService(
            this, 2,
            Intent(this, ScanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_PROTECTION)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.notif_active_title))
            .setContentText(getString(R.string.notif_active_text))
            .setOngoing(true)
            .setContentIntent(contentPi)
            .addAction(0, getString(R.string.notif_action_stop), stopPi)
            .build()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val protection = NotificationChannel(
            CHANNEL_PROTECTION,
            getString(R.string.channel_protection),
            NotificationManager.IMPORTANCE_LOW
        )
        val alerts = NotificationChannel(
            CHANNEL_ALERTS,
            getString(R.string.channel_alerts),
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(protection)
        nm.createNotificationChannel(alerts)
    }

    // ---------------------------------------------------------------- teardown

    override fun onDestroy() {
        ProtectionState.set(false)
        sessionJob?.cancel()
        scope.cancel()
        try {
            imageReader?.close()
        } catch (_: Throwable) {
        }
        try {
            virtualDisplay?.release()
        } catch (_: Throwable) {
        }
        try {
            projection?.stop()
        } catch (_: Throwable) {
        }
        handlerThread?.quitSafely()
        try {
            audio?.stop()
        } catch (_: Throwable) {
        }
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        overlay?.hide()
        projection = null
        virtualDisplay = null
        imageReader = null
        handlerThread = null
        super.onDestroy()
    }
}
