package com.medisense.app.ui.medication.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.data.local.entity.MedicationHistoryEntity
import com.medisense.app.databinding.ItemMedicationHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MedicationHistoryAdapter : ListAdapter<MedicationHistoryEntity, MedicationHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicationHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemMedicationHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun bind(item: MedicationHistoryEntity) {
            val dosageText = if (item.dosage.isNotBlank()) " (${item.dosage})" else ""
            binding.tvHistoryName.text = "${item.medicineName}$dosageText"

            val dateStr = if (item.scheduledDate > 0) dateFormat.format(Date(item.scheduledDate)) else "Today"
            binding.tvHistoryTime.text = "$dateStr • ${item.scheduledTime}"

            val status = item.status.uppercase()
            binding.tvStatusBadge.text = status
            when (status) {
                "TAKEN" -> {
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#2E7D32"))
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#E8F5E9"))
                }
                "SKIPPED" -> {
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#E65100"))
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#FFF3E0"))
                }
                "MISSED" -> {
                    binding.tvStatusBadge.setTextColor(Color.parseColor("#C62828"))
                    binding.tvStatusBadge.setBackgroundColor(Color.parseColor("#FFEBEE"))
                }
                else -> {
                    binding.tvStatusBadge.setTextColor(Color.DKGRAY)
                    binding.tvStatusBadge.setBackgroundColor(Color.LTGRAY)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MedicationHistoryEntity>() {
        override fun areItemsTheSame(oldItem: MedicationHistoryEntity, newItem: MedicationHistoryEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MedicationHistoryEntity, newItem: MedicationHistoryEntity): Boolean =
            oldItem == newItem
    }
}
