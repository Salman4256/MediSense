package com.medisense.app.ui.adaptive.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.R
import com.medisense.app.databinding.ItemInterventionResponseHistoryBinding
import com.medisense.app.domain.model.DiscrepancyCategory
import com.medisense.app.domain.model.InterventionResponseRecord
import java.text.SimpleDateFormat
import java.util.*

/**
 * ListAdapter for displaying past recorded intervention observations and discrepancy telemetry (Module 25).
 */
class InterventionResponseHistoryAdapter(
    private val onDeleteClicked: (Long) -> Unit
) : ListAdapter<InterventionResponseRecord, InterventionResponseHistoryAdapter.ViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemInterventionResponseHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(record: InterventionResponseRecord) {
            binding.chipHistoryCategory.text = record.interventionCategory.displayName
            binding.tvHistoryDescription.text = record.interventionDescription
            binding.tvExpectedResponse.text = "Expected: ${record.expectedResponse}"
            binding.tvObservedResponse.text = "Observed: ${record.observedResponse.displayName}"

            // Discrepancy category badge
            binding.tvDiscrepancyBadge.text = record.discrepancyCategory.displayName
            when (record.discrepancyCategory) {
                DiscrepancyCategory.ALIGNED -> {
                    binding.tvDiscrepancyBadge.setBackgroundResource(R.drawable.bg_badge_positive)
                }
                DiscrepancyCategory.SLIGHT_DEVIATION -> {
                    binding.tvDiscrepancyBadge.setBackgroundResource(R.drawable.bg_badge_info)
                }
                DiscrepancyCategory.MODERATE_DEVIATION,
                DiscrepancyCategory.LARGE_DEVIATION -> {
                    binding.tvDiscrepancyBadge.setBackgroundResource(R.drawable.bg_badge_attention)
                }
                DiscrepancyCategory.INSUFFICIENT_OBSERVATION -> {
                    binding.tvDiscrepancyBadge.setBackgroundResource(R.drawable.bg_badge_info)
                }
            }

            // User notes
            if (!record.userNotes.isNullOrBlank()) {
                binding.tvUserNotes.visibility = View.VISIBLE
                binding.tvUserNotes.text = "Notes: ${record.userNotes}"
            } else {
                binding.tvUserNotes.visibility = View.GONE
            }

            // Formatted date
            binding.tvObservationDate.text = dateFormat.format(Date(record.observationTimestamp))

            binding.btnDeleteRecord.setOnClickListener {
                onDeleteClicked(record.id)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInterventionResponseHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object DiffCallback : DiffUtil.ItemCallback<InterventionResponseRecord>() {
        override fun areItemsTheSame(
            oldItem: InterventionResponseRecord,
            newItem: InterventionResponseRecord
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: InterventionResponseRecord,
            newItem: InterventionResponseRecord
        ): Boolean = oldItem == newItem
    }
}
