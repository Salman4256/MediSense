package com.medisense.app.ui.prediction.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.data.model.XaiFeatureContribution
import com.medisense.app.databinding.ItemFeatureContributionBinding

class FeatureContributionAdapter : ListAdapter<XaiFeatureContribution, FeatureContributionAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFeatureContributionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemFeatureContributionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: XaiFeatureContribution) {
            binding.tvFeatureName.text = item.displayName
            val percent = (item.contribution * 100).toInt().coerceIn(5, 100)
            binding.progressContribution.progress = percent
            binding.tvContributionPercent.text = "${percent}%"
            binding.tvDirectionBadge.text = item.direction.name.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<XaiFeatureContribution>() {
        override fun areItemsTheSame(oldItem: XaiFeatureContribution, newItem: XaiFeatureContribution): Boolean =
            oldItem.featureName == newItem.featureName

        override fun areContentsTheSame(oldItem: XaiFeatureContribution, newItem: XaiFeatureContribution): Boolean =
            oldItem == newItem
    }
}
