package com.medisense.app.ui.portability.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemPortableCategoryBinding
import com.medisense.app.domain.model.PortableDataCategory
import com.medisense.app.ui.portability.viewmodel.CategoryItemUiState

class PortableCategoryAdapter(
    private val onCategoryToggled: (PortableDataCategory) -> Unit
) : ListAdapter<CategoryItemUiState, PortableCategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemPortableCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding, onCategoryToggled)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CategoryViewHolder(
        private val binding: ItemPortableCategoryBinding,
        private val onCategoryToggled: (PortableDataCategory) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CategoryItemUiState) {
            binding.cbCategory.setOnCheckedChangeListener(null)
            binding.cbCategory.isChecked = item.isSelected

            binding.tvCategoryName.text = item.category.displayName
            binding.tvCategoryDesc.text = item.category.description
            binding.chipResourceHint.text = item.category.resourceTypeHint

            if (item.recordCount > 0) {
                binding.tvRecordCount.text = "• ${item.recordCount} records available"
                binding.tvRecordCount.visibility = android.view.View.VISIBLE
            } else {
                binding.tvRecordCount.visibility = android.view.View.GONE
            }

            binding.cbCategory.setOnCheckedChangeListener { _, _ ->
                onCategoryToggled(item.category)
            }

            binding.root.setOnClickListener {
                binding.cbCategory.isChecked = !binding.cbCategory.isChecked
            }
        }
    }

    private class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryItemUiState>() {
        override fun areItemsTheSame(oldItem: CategoryItemUiState, newItem: CategoryItemUiState): Boolean {
            return oldItem.category == newItem.category
        }

        override fun areContentsTheSame(oldItem: CategoryItemUiState, newItem: CategoryItemUiState): Boolean {
            return oldItem == newItem
        }
    }
}
