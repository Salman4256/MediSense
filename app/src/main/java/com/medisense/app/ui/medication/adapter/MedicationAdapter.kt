package com.medisense.app.ui.medication.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.data.local.entity.MedicationEntity
import com.medisense.app.databinding.ItemMedicationBinding

class MedicationAdapter(
    private val onToggleActive: (MedicationEntity, Boolean) -> Unit,
    private val onMarkTaken: (MedicationEntity) -> Unit,
    private val onMarkSkipped: (MedicationEntity) -> Unit,
    private val onDelete: (MedicationEntity) -> Unit
) : ListAdapter<MedicationEntity, MedicationAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMedicationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemMedicationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MedicationEntity) {
            binding.tvMedicationName.text = item.medicineName
            binding.tvDosageFood.text = "${item.dosage} ${item.dosageUnit}"

            val times = item.scheduledTimes.joinToString(", ")
            val freqText = when (item.frequency) {
                "ONCE_DAILY" -> "Once Daily ($times)"
                "TWICE_DAILY" -> "Twice Daily ($times)"
                "THREE_TIMES_DAILY" -> "3x Daily ($times)"
                "FOUR_TIMES_DAILY" -> "4x Daily ($times)"
                else -> item.frequency + if (times.isNotBlank()) " ($times)" else ""
            }
            binding.tvFrequency.text = freqText

            if (item.instructions.isNotBlank()) {
                binding.tvInstructions.visibility = android.view.View.VISIBLE
                binding.tvInstructions.text = item.instructions
            } else {
                binding.tvInstructions.visibility = android.view.View.GONE
            }

            binding.switchActive.setOnCheckedChangeListener(null)
            binding.switchActive.isChecked = item.active
            binding.switchActive.setOnCheckedChangeListener { _, isChecked ->
                onToggleActive(item, isChecked)
            }

            binding.btnMarkTaken.setOnClickListener {
                onMarkTaken(item)
            }

            binding.btnMarkSkipped.setOnClickListener {
                onMarkSkipped(item)
            }

            binding.btnDelete.setOnClickListener {
                onDelete(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<MedicationEntity>() {
        override fun areItemsTheSame(oldItem: MedicationEntity, newItem: MedicationEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: MedicationEntity, newItem: MedicationEntity): Boolean =
            oldItem == newItem
    }
}
