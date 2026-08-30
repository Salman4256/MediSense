package com.medisense.app.ui.prediction.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.data.model.DiseasePrediction
import com.medisense.app.databinding.ItemPredictionResultBinding

class PredictionResultAdapter : ListAdapter<DiseasePrediction, PredictionResultAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPredictionResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemPredictionResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(prediction: DiseasePrediction) {
            binding.tvConditionName.text = prediction.diseaseName
            binding.tvRank.text = "Rank #${prediction.rank}"
            binding.tvPercentage.text = String.format("%.0f%%", prediction.probability * 100)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DiseasePrediction>() {
        override fun areItemsTheSame(oldItem: DiseasePrediction, newItem: DiseasePrediction): Boolean = oldItem.diseaseName == newItem.diseaseName
        override fun areContentsTheSame(oldItem: DiseasePrediction, newItem: DiseasePrediction): Boolean = oldItem == newItem
    }
}
