package com.medisense.app.ui.privacy.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.databinding.ItemSecurityAuditEventBinding
import com.medisense.app.domain.model.SecurityAuditEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SecurityAuditAdapter : ListAdapter<SecurityAuditEvent, SecurityAuditAdapter.AuditViewHolder>(AuditDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AuditViewHolder {
        val binding = ItemSecurityAuditEventBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AuditViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AuditViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AuditViewHolder(
        private val binding: ItemSecurityAuditEventBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        fun bind(event: SecurityAuditEvent) {
            binding.tvEventTypeBadge.text = event.eventType.name
            binding.tvEventDescription.text = event.description
            binding.tvEventTimestamp.text = dateFormat.format(Date(event.timestamp))
        }
    }

    private class AuditDiffCallback : DiffUtil.ItemCallback<SecurityAuditEvent>() {
        override fun areItemsTheSame(oldItem: SecurityAuditEvent, newItem: SecurityAuditEvent): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SecurityAuditEvent, newItem: SecurityAuditEvent): Boolean {
            return oldItem == newItem
        }
    }
}
