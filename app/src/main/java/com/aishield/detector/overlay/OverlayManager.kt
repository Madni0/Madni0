package com.aishield.detector.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.aishield.detector.core.AppConfig
import com.aishield.detector.databinding.ViewOverlayChipBinding
import com.aishield.detector.ui.DetailActivity
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Draws the small "Likely AI - 94%" warning over the current app using a
 * TYPE_APPLICATION_OVERLAY window. Tapping it opens the detailed analysis.
 * The chip auto-dismisses; it is the ONLY UI the user ever sees while
 * scrolling (no scanning indicators, per client requirement).
 */
class OverlayManager(private val context: Context) {

    private val wm =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var chipView: View? = null
    private val hideRunnable = Runnable { hideInternal() }

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    /**
     * Shows the chip. Blocks briefly (called from a service worker thread) so
     * the caller knows whether the overlay actually appeared, falling back to
     * a notification otherwise.
     */
    fun show(id: Long, pct: Int, cfg: AppConfig): Boolean {
        if (!canDraw()) return false
        var success = false
        val latch = CountDownLatch(1)
        mainHandler.post {
            try {
                hideInternal()
                val binding = ViewOverlayChipBinding.inflate(LayoutInflater.from(context))
                binding.tvChipText.text = context.getString(
                    com.aishield.detector.R.string.chip_text, pct
                )
                binding.root.setOnClickListener {
                    openDetail(id)
                    hideInternal()
                }
                binding.btnChipClose.setOnClickListener { hideInternal() }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
                )
                val top = cfg.overlayPosition != "bottom"
                params.gravity = (if (top) Gravity.TOP else Gravity.BOTTOM) or Gravity.END
                params.x = dp(12)
                params.y = dp(48)
                wm.addView(binding.root, params)
                chipView = binding.root
                mainHandler.postDelayed(hideRunnable, cfg.overlayAutoDismissMs)
                success = true
            } catch (_: Throwable) {
                success = false
            } finally {
                latch.countDown()
            }
        }
        latch.await(1, TimeUnit.SECONDS)
        return success
    }

    fun hide() {
        mainHandler.post { hideInternal() }
    }

    private fun hideInternal() {
        mainHandler.removeCallbacks(hideRunnable)
        chipView?.let { runCatching { wm.removeView(it) } }
        chipView = null
    }

    private fun openDetail(id: Long) {
        runCatching {
            context.startActivity(
                Intent(context, DetailActivity::class.java)
                    .putExtra(DetailActivity.EXTRA_ID, id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun dp(v: Int): Int =
        (v * context.resources.displayMetrics.density).toInt()
}
