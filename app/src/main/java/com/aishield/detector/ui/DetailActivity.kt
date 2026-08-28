package com.aishield.detector.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.aishield.detector.App
import com.aishield.detector.R
import com.aishield.detector.core.DetectionDb
import com.aishield.detector.detection.C2paStatus
import com.aishield.detector.databinding.ActivityDetailBinding
import com.aishield.detector.util.AppNames
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Detailed breakdown of one detection (tapping an overlay chip opens this). */
class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "detection_id"
    }

    private lateinit var binding: ActivityDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val row = (application as App).db.byId(id)
        if (row == null) {
            finish()
            return
        }
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val pct = row.overall.times(100).roundToInt()
        binding.tvOverallScore.text = getString(R.string.percent, pct)
        binding.tvOverallScore.setTextColor(
            ContextCompat.getColor(this, ScoreStyle.colorRes(row.overall))
        )
        binding.tvVerdict.text = getString(ScoreStyle.verdict(row.overall))

        bindBar(
            binding.progressVisual, binding.tvVisual,
            row.visual, getString(R.string.label_visual)
        )
        bindBarNullable(
            binding.progressDeepfake, binding.rowDeepfake,
            binding.tvDeepfake, row.deepfake, getString(R.string.label_deepfake)
        )
        bindBarNullable(
            binding.progressAudio, binding.rowAudio,
            binding.tvAudio, row.audio, getString(R.string.label_audio)
        )

        binding.tvC2pa.text = runCatching { C2paStatus.valueOf(row.c2pa).display() }
            .getOrDefault(getString(R.string.c2pa_not_detected))
        binding.tvApp.text = getString(R.string.detected_in, AppNames.pretty(row.packageName))
        binding.tvTime.text = DateFormat.getDateTimeInstance()
            .format(Date(row.timeMs))

        if (row.thumbPath != null && File(row.thumbPath).exists()) {
            binding.imgThumb.setImageBitmap(BitmapFactory.decodeFile(row.thumbPath))
        } else {
            binding.imgThumb.visibility = View.GONE
        }

        binding.tvExplanation.text = getString(R.string.detail_explanation)
        binding.tvC2paNote.text = getString(R.string.c2pa_note)
        binding.btnDismiss.setOnClickListener { finish() }
    }

    private fun bindBar(
        bar: com.google.android.material.progressindicator.LinearProgressIndicator,
        label: android.widget.TextView,
        value: Double,
        prefix: String
    ) {
        val pct = value.times(100).roundToInt()
        bar.setProgressCompat(pct, true)
        label.text = "$prefix - $pct%"
    }

    private fun bindBarNullable(
        bar: com.google.android.material.progressindicator.LinearProgressIndicator,
        rowView: View,
        label: android.widget.TextView,
        value: Double?,
        prefix: String
    ) {
        if (value == null) {
            rowView.visibility = View.GONE
            return
        }
        rowView.visibility = View.VISIBLE
        bindBar(bar, label, value, prefix)
    }
}
