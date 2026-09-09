package com.medisense.app.ui.analytics.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.R
import com.medisense.app.databinding.ItemTemporalPatternBinding
import com.medisense.app.domain.model.PatternSeverity
import com.medisense.app.domain.model.TemporalPattern
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TemporalPatternAdapter : ListAdapter<TemporalPattern, TemporalPatternAdapter.PatternViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PatternViewHolder {
        val binding = ItemTemporalPatternBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PatternViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PatternViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PatternViewHolder(
        private val binding: ItemTemporalPatternBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(pattern: TemporalPattern) {
            binding.tvPatternCategory.text = pattern.category.name
            binding.tvPatternTitle.text = pattern.title
            binding.tvPatternDescription.text = pattern.description

            // Severity Badge
            when (pattern.severity) {
                PatternSeverity.POSITIVE -> {
                    binding.tvPatternSeverityBadge.setBackgroundResource(R.drawable.bg_badge_positive)
                    binding.tvPatternSeverityBadge.text = "Positive"
                }
                PatternSeverity.ATTENTION -> {
                    binding.tvPatternSeverityBadge.setBackgroundResource(R.drawable.bg_badge_attention)
                    binding.tvPatternSeverityBadge.text = "Attention"
                }
                PatternSeverity.INFO -> {
                    binding.tvPatternSeverityBadge.setBackgroundResource(R.drawable.bg_badge_info)
                    binding.tvPatternSeverityBadge.text = "Notice"
                }
            }

            // Dates footer if available
            if (pattern.firstObservedDate != null && pattern.lastObservedDate != null) {
                val firstDate = dateFormat.format(Date(pattern.firstObservedDate))
                val lastDate = dateFormat.format(Date(pattern.lastObservedDate))
                binding.tvPatternFooter.visibility = View.VISIBLE
                binding.tvPatternFooter.text = "First: $firstDate • Last: $lastDate"
            } else {
                binding.tvPatternFooter.visibility = View.GONE
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<TemporalPattern>() {
        override fun areItemsTheSame(oldItem: TemporalPattern, newItem: TemporalPattern): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TemporalPattern, newItem: TemporalPattern): Boolean {
            return oldItem == newItem
        }
    }
}
