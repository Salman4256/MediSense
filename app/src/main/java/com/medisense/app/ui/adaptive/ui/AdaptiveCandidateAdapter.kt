package com.medisense.app.ui.adaptive.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemAdaptiveCandidateCardBinding
import com.medisense.app.domain.model.AdaptiveCounterfactualCandidate

/**
 * ListAdapter for displaying adaptively re-ranked counterfactual candidates (Module 25).
 */
class AdaptiveCandidateAdapter(
    private val onRecordObservationClicked: (AdaptiveCounterfactualCandidate) -> Unit
) : ListAdapter<AdaptiveCounterfactualCandidate, AdaptiveCandidateAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemAdaptiveCandidateCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(candidate: AdaptiveCounterfactualCandidate) {
            binding.tvRank.text = "#${candidate.rank}"
            binding.chipCategory.text = candidate.category.displayName
            val scorePct = (candidate.adaptiveScore * 100).toInt()
            binding.tvAdaptiveScore.text = "Score: $scorePct%"
            binding.tvCandidateTitle.text = candidate.title
            binding.tvCandidateDescription.text = candidate.description
            binding.tvExpectedShift.text = candidate.expectedShiftDescription
            binding.tvRankingRationale.text = candidate.rankingRationale

            binding.btnRecordObservation.setOnClickListener {
                onRecordObservationClicked(candidate)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdaptiveCandidateCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AdaptiveCounterfactualCandidate>() {
        override fun areItemsTheSame(
            oldItem: AdaptiveCounterfactualCandidate,
            newItem: AdaptiveCounterfactualCandidate
        ): Boolean = oldItem.scenarioId == newItem.scenarioId

        override fun areContentsTheSame(
            oldItem: AdaptiveCounterfactualCandidate,
            newItem: AdaptiveCounterfactualCandidate
        ): Boolean = oldItem == newItem
    }
}
