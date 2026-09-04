package com.medisense.app.ui.assistant.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.data.local.entity.ConversationEntity
import com.medisense.app.databinding.ItemChatHistoryBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatHistoryAdapter(
    private val onChatClick: (ConversationEntity) -> Unit,
    private val onDeleteClick: (ConversationEntity) -> Unit
) : ListAdapter<ConversationEntity, ChatHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemChatHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())

        fun bind(item: ConversationEntity) {
            binding.tvPreview.text = item.title
            binding.tvDate.text = dateFormat.format(Date(item.updatedAt))

            binding.root.setOnClickListener {
                onChatClick(item)
            }

            binding.btnDelete.setOnClickListener {
                onDeleteClick(item)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ConversationEntity>() {
        override fun areItemsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ConversationEntity, newItem: ConversationEntity): Boolean =
            oldItem == newItem
    }
}
