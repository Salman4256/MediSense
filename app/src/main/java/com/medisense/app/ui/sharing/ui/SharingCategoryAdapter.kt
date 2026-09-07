package com.medisense.app.ui.sharing.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemSharingCategoryBinding
import com.medisense.app.domain.model.SharingDataCategory

class SharingCategoryAdapter(
    private val onCategoryToggled: (SharingDataCategory, Boolean) -> Unit
) : RecyclerView.Adapter<SharingCategoryAdapter.CategoryViewHolder>() {

    private val categories = SharingDataCategory.entries.toList()
    private val selectedCategories = mutableSetOf<SharingDataCategory>()

    fun updateSelected(selected: Set<SharingDataCategory>) {
        selectedCategories.clear()
        selectedCategories.addAll(selected)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemSharingCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(categories[position])
    }

    override fun getItemCount(): Int = categories.size

    inner class CategoryViewHolder(private val binding: ItemSharingCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: SharingDataCategory) {
            binding.tvCategoryName.text = category.displayName
            binding.tvCategoryDesc.text = category.description

            val isChecked = selectedCategories.contains(category)
            binding.cbCategory.setOnCheckedChangeListener(null)
            binding.cbCategory.isChecked = isChecked

            binding.cbCategory.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    selectedCategories.add(category)
                } else {
                    selectedCategories.remove(category)
                }
                onCategoryToggled(category, checked)
            }

            binding.cardCategory.setOnClickListener {
                binding.cbCategory.isChecked = !binding.cbCategory.isChecked
            }
        }
    }
}
