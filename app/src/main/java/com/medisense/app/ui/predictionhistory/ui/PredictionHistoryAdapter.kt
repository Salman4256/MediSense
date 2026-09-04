package com.medisense.app.ui.predictionhistory.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.data.local.entity.PredictionHistoryEntity
import com.medisense.app.databinding.ItemPredictionHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PredictionHistoryAdapter(
    private val onItemClick: (PredictionHistoryEntity) -> Unit,
    private val onDeleteClick: (PredictionHistoryEntity) -> Unit
) : ListAdapter<PredictionHistoryEntity, PredictionHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPredictionHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemPredictionHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

        fun bind(item: PredictionHistoryEntity) {
            binding.tvDiseaseName.text = item.predictedDisease.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

            binding.tvPredictionDate.text = dateFormat.format(Date(item.predictionTimestamp))

            val confidencePercent = (item.confidence * 100).toInt().coerceIn(1, 100)
            binding.tvConfidence.text = "Model confidence: $confidencePercent%"

            val symptomsSummary = if (item.symptoms.isNotEmpty()) {
                "Symptoms: " + item.symptoms.take(4).joinToString(", ") + if (item.symptoms.size > 4) ", ..." else ""
            } else {
                "No symptom details"
            }
            binding.tvSymptomsPreview.text = symptomsSummary

            binding.root.setOnClickListener {
                onItemClick(item)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PredictionHistoryEntity>() {
        override fun areItemsTheSame(oldItem: PredictionHistoryEntity, newItem: PredictionHistoryEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PredictionHistoryEntity, newItem: PredictionHistoryEntity): Boolean =
            oldItem == newItem
    }
}
