package com.medisense.app.ui.timeline.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.R
import com.medisense.app.databinding.ItemHealthTimelineEventBinding
import com.medisense.app.domain.model.HealthTimelineCategoryFilter
import com.medisense.app.domain.model.HealthTimelineEvent
import com.medisense.app.domain.model.HealthTimelinePriority
import java.text.SimpleDateFormat
import java.util.*

class HealthTimelineAdapter(
    private val onEventClicked: (HealthTimelineEvent) -> Unit
) : ListAdapter<HealthTimelineEvent, HealthTimelineAdapter.TimelineViewHolder>(EventDiffCallback) {

    private val dateFormatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val binding = ItemHealthTimelineEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TimelineViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        val item = getItem(position)
        val isFirst = position == 0
        val isLast = position == itemCount - 1
        holder.bind(item, isFirst, isLast)
    }

    inner class TimelineViewHolder(
        private val binding: ItemHealthTimelineEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(event: HealthTimelineEvent, isFirst: Boolean, isLast: Boolean) {
            binding.tvEventTitle.text = event.title
            binding.tvEventDescription.text = event.shortDescription
            binding.tvCategoryBadge.text = event.eventType.displayName
            binding.tvSourceAttribution.text = event.source.displayName

            // Formatted date/time
            try {
                binding.tvEventTime.text = dateFormatter.format(Date(event.timestamp))
            } catch (e: Exception) {
                binding.tvEventTime.text = "${event.timestamp}"
            }

            // Timeline line continuity
            binding.timelineLineTop.visibility = if (isFirst) View.INVISIBLE else View.VISIBLE
            binding.timelineLineBottom.visibility = if (isLast) View.INVISIBLE else View.VISIBLE

            // Priority Badge
            if (event.priority == HealthTimelinePriority.IMPORTANT || event.priority == HealthTimelinePriority.ATTENTION) {
                binding.tvPriorityBadge.visibility = View.VISIBLE
                binding.tvPriorityBadge.text = if (event.priority == HealthTimelinePriority.ATTENTION) "Attention" else "Important"
            } else {
                binding.tvPriorityBadge.visibility = View.GONE
            }

            // Category-specific iconography
            val iconRes = when (event.eventType.category) {
                HealthTimelineCategoryFilter.PREDICTIONS -> R.drawable.ic_insights
                HealthTimelineCategoryFilter.MEDICATIONS -> R.drawable.ic_pill
                HealthTimelineCategoryFilter.APPOINTMENTS -> R.drawable.ic_calendar
                HealthTimelineCategoryFilter.TRENDS -> R.drawable.ic_ai
                HealthTimelineCategoryFilter.CONTEXT_RISK -> R.drawable.ic_check_circle
                HealthTimelineCategoryFilter.DATA_QUALITY -> R.drawable.ic_info
                HealthTimelineCategoryFilter.REPORTS_SYNC -> R.drawable.ic_sync
                HealthTimelineCategoryFilter.ALL -> R.drawable.ic_history
            }
            binding.ivEventIcon.setImageResource(iconRes)

            // Click listener to inspect details
            binding.cardTimelineEvent.setOnClickListener {
                onEventClicked(event)
            }
        }
    }

    companion object EventDiffCallback : DiffUtil.ItemCallback<HealthTimelineEvent>() {
        override fun areItemsTheSame(oldItem: HealthTimelineEvent, newItem: HealthTimelineEvent): Boolean {
            return oldItem.eventId == newItem.eventId
        }

        override fun areContentsTheSame(oldItem: HealthTimelineEvent, newItem: HealthTimelineEvent): Boolean {
            return oldItem == newItem
        }
    }
}
