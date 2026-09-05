package com.medisense.app.ui.rchr.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemReconstructedAttributeBinding
import com.medisense.app.domain.rchr.ReconstructedAttribute

class ReconstructedAttributeAdapter : ListAdapter<ReconstructedAttribute, ReconstructedAttributeAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReconstructedAttributeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemReconstructedAttributeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ReconstructedAttribute) {
            binding.tvAttributeCategory.text = item.category
            binding.tvAttributeKey.text = item.attributeKey
            binding.tvEncodedValue.text = item.encodedValue
            binding.tvReconstructedMeaning.text = item.humanReadableMeaning
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ReconstructedAttribute>() {
        override fun areItemsTheSame(oldItem: ReconstructedAttribute, newItem: ReconstructedAttribute): Boolean =
            oldItem.attributeKey == newItem.attributeKey

        override fun areContentsTheSame(oldItem: ReconstructedAttribute, newItem: ReconstructedAttribute): Boolean =
            oldItem == newItem
    }
}
