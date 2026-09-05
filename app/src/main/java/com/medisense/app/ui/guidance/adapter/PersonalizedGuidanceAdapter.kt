package com.medisense.app.ui.guidance.adapter

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemPersonalizedGuidanceBinding
import com.medisense.app.domain.model.GuidanceActionType
import com.medisense.app.domain.model.GuidancePriority
import com.medisense.app.domain.model.PersonalizedGuidance

class PersonalizedGuidanceAdapter(
    private val onActionClick: (PersonalizedGuidance) -> Unit
) : ListAdapter<PersonalizedGuidance, PersonalizedGuidanceAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPersonalizedGuidanceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onActionClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemPersonalizedGuidanceBinding,
        private val onActionClick: (PersonalizedGuidance) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: PersonalizedGuidance) {
            binding.chipCategory.text = item.category.displayName
            binding.chipPriority.text = item.priority.label

            when (item.priority) {
                GuidancePriority.HIGH -> {
                    binding.chipPriority.setTextColor(Color.parseColor("#C62828"))
                    binding.chipPriority.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#22C62828"))
                }
                GuidancePriority.MEDIUM -> {
                    binding.chipPriority.setTextColor(Color.parseColor("#E65100"))
                    binding.chipPriority.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#22E65100"))
                }
                GuidancePriority.LOW -> {
                    binding.chipPriority.setTextColor(Color.parseColor("#2E7D32"))
                    binding.chipPriority.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#222E7D32"))
                }
            }

            binding.tvGuidanceTitle.text = item.title
            binding.tvGuidanceMessage.text = item.message
            binding.tvGuidanceExplanation.text = item.explanation
            binding.tvGuidanceSources.text = "Sources: ${item.sources.joinToString(" • ")}"

            val hasAction = item.actionType != GuidanceActionType.NONE && !item.actionLabel.isNullOrBlank()
            binding.btnGuidanceAction.isVisible = hasAction
            if (hasAction) {
                binding.btnGuidanceAction.text = item.actionLabel
                binding.btnGuidanceAction.setOnClickListener {
                    onActionClick(item)
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PersonalizedGuidance>() {
        override fun areItemsTheSame(oldItem: PersonalizedGuidance, newItem: PersonalizedGuidance): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: PersonalizedGuidance, newItem: PersonalizedGuidance): Boolean =
            oldItem == newItem
    }
}
