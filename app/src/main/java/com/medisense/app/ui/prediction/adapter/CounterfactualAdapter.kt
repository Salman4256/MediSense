package com.medisense.app.ui.prediction.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemCounterfactualCardBinding
import com.medisense.app.domain.model.CounterfactualExplanation
import kotlin.math.abs
import kotlin.math.roundToInt

class CounterfactualAdapter : ListAdapter<CounterfactualExplanation, CounterfactualAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCounterfactualCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemCounterfactualCardBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CounterfactualExplanation) {
            binding.tvScenarioTitle.text = "WHAT IF \"${item.removedSymptomDisplayName.uppercase()}\" WERE REMOVED?"

            if (item.isPredictionChanged) {
                binding.chipChangeType.text = "Outcome Changed"
                binding.chipChangeType.setTextColor(Color.parseColor("#C62828"))
                binding.chipChangeType.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#22C62828"))
            } else if (abs(item.confidenceDelta) >= 0.01f) {
                binding.chipChangeType.text = "Confidence Shift"
                binding.chipChangeType.setTextColor(Color.parseColor("#1565C0"))
                binding.chipChangeType.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#221565C0"))
            } else {
                binding.chipChangeType.text = "No Change"
                binding.chipChangeType.setTextColor(Color.parseColor("#757575"))
                binding.chipChangeType.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#22757575"))
            }

            val origPct = (item.originalConfidence * 100).roundToInt()
            val newPct = (item.resultingConfidence * 100).roundToInt()

            binding.tvOrigDisease.text = item.originalPrediction
            binding.tvOrigConfidence.text = "$origPct% confidence"

            binding.tvNewDisease.text = item.resultingPrediction
            binding.tvNewConfidence.text = "$newPct% confidence"

            binding.tvCounterfactualExplanation.text = item.explanation
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<CounterfactualExplanation>() {
        override fun areItemsTheSame(oldItem: CounterfactualExplanation, newItem: CounterfactualExplanation): Boolean =
            oldItem.removedSymptom == newItem.removedSymptom

        override fun areContentsTheSame(oldItem: CounterfactualExplanation, newItem: CounterfactualExplanation): Boolean =
            oldItem == newItem
    }
}
