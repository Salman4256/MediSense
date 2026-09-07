package com.medisense.app.ui.quality.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.R
import com.medisense.app.databinding.ItemHealthDataQualityIssueBinding
import com.medisense.app.domain.model.HealthDataQualityIssue
import com.medisense.app.domain.model.HealthDataQualitySeverity

class HealthDataQualityAdapter(
    private val onFixClicked: (HealthDataQualityIssue) -> Unit
) : ListAdapter<HealthDataQualityIssue, HealthDataQualityAdapter.IssueViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IssueViewHolder {
        val binding = ItemHealthDataQualityIssueBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return IssueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IssueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class IssueViewHolder(
        private val binding: ItemHealthDataQualityIssueBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(issue: HealthDataQualityIssue) {
            binding.tvIssueTitle.text = issue.title
            binding.tvIssueExplanation.text = issue.explanation
            binding.tvIssueSuggestion.text = issue.suggestedCorrection
            binding.tvIssueCategory.text = issue.category.name.replace("_", " ")

            // Severity styling
            binding.tvIssueSeverity.text = issue.severity.name
            when (issue.severity) {
                HealthDataQualitySeverity.ERROR -> {
                    binding.tvIssueSeverity.setBackgroundResource(R.drawable.bg_badge_attention)
                }
                HealthDataQualitySeverity.WARNING -> {
                    binding.tvIssueSeverity.setBackgroundResource(R.drawable.bg_badge_supports)
                }
                HealthDataQualitySeverity.INFO -> {
                    binding.tvIssueSeverity.setBackgroundResource(R.drawable.bg_badge_info)
                }
            }

            // Fix action button visibility
            binding.btnFixIssue.isVisible = issue.navigationDestinationId != null
            binding.btnFixIssue.setOnClickListener {
                onFixClicked(issue)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<HealthDataQualityIssue>() {
        override fun areItemsTheSame(oldItem: HealthDataQualityIssue, newItem: HealthDataQualityIssue): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HealthDataQualityIssue, newItem: HealthDataQualityIssue): Boolean {
            return oldItem == newItem
        }
    }
}
