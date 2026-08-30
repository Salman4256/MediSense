package com.medisense.app.ui.prediction.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.data.model.Symptom
import com.medisense.app.databinding.ItemSymptomBinding

class SymptomAdapter(
    private val onSymptomClicked: (Symptom, Boolean) -> Unit
) : ListAdapter<Symptom, SymptomAdapter.SymptomViewHolder>(SymptomDiffCallback()) {

    private var selectedSymptoms: Set<Symptom> = emptySet()

    fun updateSelected(selected: List<Symptom>) {
        this.selectedSymptoms = selected.toSet()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SymptomViewHolder {
        val binding = ItemSymptomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SymptomViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SymptomViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SymptomViewHolder(private val binding: ItemSymptomBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(symptom: Symptom) {
            binding.tvSymptomName.text = symptom.displayName
            val isSelected = selectedSymptoms.contains(symptom)
            binding.cbSelect.isChecked = isSelected

            binding.root.setOnClickListener {
                val newState = !binding.cbSelect.isChecked
                binding.cbSelect.isChecked = newState
                onSymptomClicked(symptom, newState)
            }
        }
    }

    class SymptomDiffCallback : DiffUtil.ItemCallback<Symptom>() {
        override fun areItemsTheSame(oldItem: Symptom, newItem: Symptom): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Symptom, newItem: Symptom): Boolean = oldItem == newItem
    }
}
