package com.medisense.app.ui.analytics.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemRecurringSymptomBinding
import com.medisense.app.domain.model.RecurringSymptom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecurringSymptomAdapter : ListAdapter<RecurringSymptom, RecurringSymptomAdapter.SymptomViewHolder>(DiffCallback) {

    private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymptomViewHolder {
        val binding = ItemRecurringSymptomBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SymptomViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SymptomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SymptomViewHolder(
        private val binding: ItemRecurringSymptomBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(symptom: RecurringSymptom) {
            binding.tvSymptomName.text = symptom.symptomName
            binding.tvOccurrenceBadge.text = "${symptom.occurrenceCount}x logged"

            val firstStr = dateFormat.format(Date(symptom.firstObservedDate))
            val lastStr = dateFormat.format(Date(symptom.lastObservedDate))
            binding.tvSymptomTimeline.text = "First: $firstStr • Last: $lastStr"
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<RecurringSymptom>() {
        override fun areItemsTheSame(oldItem: RecurringSymptom, newItem: RecurringSymptom): Boolean {
            return oldItem.symptomName == newItem.symptomName
        }

        override fun areContentsTheSame(oldItem: RecurringSymptom, newItem: RecurringSymptom): Boolean {
            return oldItem == newItem
        }
    }
}
