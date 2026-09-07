package com.medisense.app.ui.trace.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemHealthDecisionTraceBinding
import com.medisense.app.domain.model.HealthDecisionTrace
import com.medisense.app.domain.model.HealthDecisionTraceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HealthDecisionTraceAdapter(
    private val onInspectClicked: (HealthDecisionTrace) -> Unit,
    private val onShareClicked: (HealthDecisionTrace) -> Unit
) : ListAdapter<HealthDecisionTrace, HealthDecisionTraceAdapter.TraceViewHolder>(TraceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TraceViewHolder {
        val binding = ItemHealthDecisionTraceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TraceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TraceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TraceViewHolder(
        private val binding: ItemHealthDecisionTraceBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

        fun bind(trace: HealthDecisionTrace) {
            binding.tvTraceTypeBadge.text = formatTypeBadge(trace.decisionType)
            binding.tvTraceEngine.text = trace.engineName
            binding.tvTraceTimestamp.text = dateFormat.format(Date(trace.generatedAt))
            binding.tvTraceSummary.text = trace.outputResult.primaryOutput

            // Confidence / Status badge
            if (!trace.outputResult.confidenceOrStatus.isNullOrBlank()) {
                binding.tvTraceConfidenceBadge.visibility = View.VISIBLE
                binding.tvTraceConfidenceBadge.text = trace.outputResult.confidenceOrStatus
            } else {
                binding.tvTraceConfidenceBadge.visibility = View.GONE
            }

            // Factors preview
            if (trace.inputFactors.isNotEmpty()) {
                val topFactors = trace.inputFactors.take(3).joinToString(", ") { factor ->
                    val weightStr = factor.weightPercentage?.let { " ($it%)" } ?: ""
                    "${factor.name}$weightStr"
                }
                binding.tvTraceFactorsPreview.text = "Key Factors: $topFactors"
                binding.tvTraceFactorsPreview.visibility = View.VISIBLE
            } else {
                binding.tvTraceFactorsPreview.visibility = View.GONE
            }

            // Click listeners
            binding.btnInspectTrace.setOnClickListener { onInspectClicked(trace) }
            binding.cardTraceItem.setOnClickListener { onInspectClicked(trace) }
            binding.btnShareTrace.setOnClickListener { onShareClicked(trace) }
        }

        private fun formatTypeBadge(type: HealthDecisionTraceType): String {
            return type.categoryBadge.uppercase()
        }
    }

    private class TraceDiffCallback : DiffUtil.ItemCallback<HealthDecisionTrace>() {
        override fun areItemsTheSame(oldItem: HealthDecisionTrace, newItem: HealthDecisionTrace): Boolean {
            return oldItem.traceId == newItem.traceId
        }

        override fun areContentsTheSame(oldItem: HealthDecisionTrace, newItem: HealthDecisionTrace): Boolean {
            return oldItem == newItem
        }
    }
}
