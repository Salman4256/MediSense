package com.medisense.app.ui.sharing.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemSharingHistoryBinding
import com.medisense.app.domain.model.HealthDataSharingConsent
import com.medisense.app.domain.model.SharingConsentStatus
import java.text.SimpleDateFormat
import java.util.*

class SharingHistoryAdapter(
    private val onRevokeClicked: (HealthDataSharingConsent) -> Unit,
    private val onDeleteClicked: (HealthDataSharingConsent) -> Unit
) : ListAdapter<HealthDataSharingConsent, SharingHistoryAdapter.HistoryViewHolder>(ConsentDiffCallback()) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemSharingHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(private val binding: ItemSharingHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HealthDataSharingConsent) {
            binding.tvHistoryRecipient.text = item.recipientLabel.ifBlank { "Designated Recipient" }
            binding.tvHistoryPurpose.text = item.purpose.displayName

            val catCount = item.selectedCategories.size
            val catNames = item.selectedCategories.joinToString(", ") { it.displayName.substringBefore("&").trim() }
            binding.tvHistoryCategories.text = "Sections ($catCount): $catNames"

            val dateStr = dateFormat.format(Date(item.createdAt))
            val fpShort = item.packageFingerprint.take(10)
            binding.tvHistoryMeta.text = "Format: ${item.format.displayName.substringBefore("(").trim()}  |  $dateStr  |  Hash: $fpShort..."

            // Status Chip styling
            binding.chipHistoryStatus.text = item.status.displayName
            if (item.status == SharingConsentStatus.ACTIVE) {
                binding.chipHistoryStatus.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#DCFCE7")) // Green 100
                binding.chipHistoryStatus.setTextColor(Color.parseColor("#15803D")) // Green 700
                binding.btnRevokeConsent.visibility = View.VISIBLE
            } else {
                binding.chipHistoryStatus.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F1F5F9")) // Slate 100
                binding.chipHistoryStatus.setTextColor(Color.parseColor("#64748B")) // Slate 500
                binding.btnRevokeConsent.visibility = View.GONE
            }

            binding.btnRevokeConsent.setOnClickListener {
                onRevokeClicked(item)
            }

            binding.btnDeleteConsent.setOnClickListener {
                onDeleteClicked(item)
            }
        }
    }

    private class ConsentDiffCallback : DiffUtil.ItemCallback<HealthDataSharingConsent>() {
        override fun areItemsTheSame(oldItem: HealthDataSharingConsent, newItem: HealthDataSharingConsent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: HealthDataSharingConsent, newItem: HealthDataSharingConsent): Boolean {
            return oldItem == newItem
        }
    }
}
