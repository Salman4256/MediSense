package com.medisense.app.ui.risk.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemContextualRiskFactorBinding
import com.medisense.app.domain.model.ContextualRiskFactor
import com.medisense.app.domain.model.FactorEffectDirection
import kotlin.math.roundToInt

class ContextualRiskFactorAdapter : ListAdapter<ContextualRiskFactor, ContextualRiskFactorAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContextualRiskFactorBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemContextualRiskFactorBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(factor: ContextualRiskFactor) {
            binding.tvFactorTitle.text = factor.title
            binding.tvFactorSource.text = "Source: ${factor.source}"
            binding.tvFactorExplanation.text = factor.explanation

            val pts = factor.weightedContribution.roundToInt()

            when (factor.effectDirection) {
                FactorEffectDirection.INCREASES_SCORE -> {
                    binding.chipContribution.text = "+$pts pts"
                    binding.chipContribution.setTextColor(Color.parseColor("#E65100")) // Orange / amber accent
                    binding.chipContribution.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#33E65100"))
                }
                FactorEffectDirection.DECREASES_SCORE -> {
                    binding.chipContribution.text = "Mitigating"
                    binding.chipContribution.setTextColor(Color.parseColor("#2E7D32")) // Green
                    binding.chipContribution.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#332E7D32"))
                }
                FactorEffectDirection.NEUTRAL -> {
                    binding.chipContribution.text = "Baseline"
                    binding.chipContribution.setTextColor(Color.parseColor("#1565C0")) // Blue
                    binding.chipContribution.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#331565C0"))
                }
                FactorEffectDirection.UNAVAILABLE -> {
                    binding.chipContribution.text = "No Data"
                    binding.chipContribution.setTextColor(Color.parseColor("#757575")) // Gray
                    binding.chipContribution.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#33757575"))
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ContextualRiskFactor>() {
        override fun areItemsTheSame(oldItem: ContextualRiskFactor, newItem: ContextualRiskFactor): Boolean =
            oldItem.factorId == newItem.factorId

        override fun areContentsTheSame(oldItem: ContextualRiskFactor, newItem: ContextualRiskFactor): Boolean =
            oldItem == newItem
    }
}
