package com.medisense.app.ui.portability.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemPortableImportResourceBinding
import com.medisense.app.domain.model.PortableImportCategoryPreview

class PortableImportAdapter(
    private val onCategoryClicked: (PortableImportCategoryPreview) -> Unit
) : ListAdapter<PortableImportCategoryPreview, PortableImportAdapter.ImportCategoryViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImportCategoryViewHolder {
        val binding = ItemPortableImportResourceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImportCategoryViewHolder(binding, onCategoryClicked)
    }

    override fun onBindViewHolder(holder: ImportCategoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ImportCategoryViewHolder(
        private val binding: ItemPortableImportResourceBinding,
        private val onCategoryClicked: (PortableImportCategoryPreview) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PortableImportCategoryPreview) {
            binding.tvImportCategoryName.text = item.displayName
            binding.chipImportStatus.text = item.statusText

            val recordPlural = if (item.recordCount == 1) "record" else "records"
            binding.tvImportRecordCount.text = "${item.recordCount} $recordPlural compiled in export"

            if (item.summaryLines.isNotEmpty()) {
                binding.tvImportSummaryPreview.text = item.summaryLines.joinToString("\n")
                binding.tvImportSummaryPreview.visibility = View.VISIBLE
            } else {
                binding.tvImportSummaryPreview.visibility = View.GONE
            }

            if (item.hasWarnings || item.hasErrors) {
                binding.layoutConflictIndicator.visibility = View.VISIBLE
                binding.tvConflictNotice.text = if (item.hasErrors) {
                    "Blocking validation errors detected in this category"
                } else {
                    "Non-blocking notices or differences detected"
                }
            } else {
                binding.layoutConflictIndicator.visibility = View.GONE
            }

            binding.root.setOnClickListener {
                onCategoryClicked(item)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<PortableImportCategoryPreview>() {
        override fun areItemsTheSame(
            oldItem: PortableImportCategoryPreview,
            newItem: PortableImportCategoryPreview
        ): Boolean {
            return oldItem.category == newItem.category
        }

        override fun areContentsTheSame(
            oldItem: PortableImportCategoryPreview,
            newItem: PortableImportCategoryPreview
        ): Boolean {
            return oldItem == newItem
        }
    }
}
