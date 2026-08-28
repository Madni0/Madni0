package com.aishield.detector.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.aishield.detector.core.DetectionDb
import com.aishield.detector.databinding.ItemDetectionBinding
import com.aishield.detector.util.AppNames
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

class HistoryAdapter(
    private val onClick: (Long) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = mutableListOf<DetectionDb.DetectionRow>()

    fun submit(rows: List<DetectionDb.DetectionRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    fun isEmpty(): Boolean = items.isEmpty()

    class VH(val binding: ItemDetectionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemDetectionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val ctx = holder.binding.root.context
        val pct = row.overall.times(100).roundToInt()

        holder.binding.tvScore.text = ctx.getString(com.aishield.detector.R.string.percent, pct)
        holder.binding.tvScore.setTextColor(
            ContextCompat.getColor(ctx, ScoreStyle.colorRes(row.overall))
        )
        holder.binding.tvPkg.text = AppNames.pretty(row.packageName)
        holder.binding.tvTime.text =
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(row.timeMs))
        holder.binding.tvSummary.text = ctx.getString(ScoreStyle.verdict(row.overall))
        holder.binding.root.setOnClickListener { onClick(row.id) }
    }
}
