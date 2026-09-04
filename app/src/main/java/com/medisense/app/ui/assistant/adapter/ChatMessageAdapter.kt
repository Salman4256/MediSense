package com.medisense.app.ui.assistant.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.medisense.app.data.local.entity.ChatMessageEntity
import com.medisense.app.databinding.ItemAssistantMessageBinding
import com.medisense.app.databinding.ItemTypingBinding
import com.medisense.app.databinding.ItemUserMessageBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatMessageAdapter : ListAdapter<ChatMessageEntity, RecyclerView.ViewHolder>(DiffCallback()) {

    var isTyping: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
        private const val VIEW_TYPE_TYPING = 3
    }

    override fun getItemCount(): Int {
        val count = super.getItemCount()
        return if (isTyping) count + 1 else count
    }

    override fun getItemViewType(position: Int): Int {
        if (isTyping && position == super.getItemCount()) {
            return VIEW_TYPE_TYPING
        }
        val message = getItem(position)
        return if (message.role.equals("USER", ignoreCase = true)) {
            VIEW_TYPE_USER
        } else {
            VIEW_TYPE_ASSISTANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val binding = ItemUserMessageBinding.inflate(inflater, parent, false)
                UserMessageViewHolder(binding)
            }
            VIEW_TYPE_TYPING -> {
                val binding = ItemTypingBinding.inflate(inflater, parent, false)
                TypingViewHolder(binding)
            }
            else -> {
                val binding = ItemAssistantMessageBinding.inflate(inflater, parent, false)
                AssistantMessageViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is UserMessageViewHolder) {
            holder.bind(getItem(position))
        } else if (holder is AssistantMessageViewHolder) {
            holder.bind(getItem(position))
        }
    }

    class UserMessageViewHolder(private val binding: ItemUserMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun bind(message: ChatMessageEntity) {
            binding.tvMessage.text = message.content
            binding.tvTimestamp.text = timeFormat.format(Date(message.timestamp))

            if (!message.imageUri.isNullOrBlank()) {
                binding.ivAttachedImage.visibility = View.VISIBLE
                try {
                    binding.ivAttachedImage.setImageURI(Uri.parse(message.imageUri))
                } catch (ignored: Exception) {
                    binding.ivAttachedImage.visibility = View.GONE
                }
            } else {
                binding.ivAttachedImage.visibility = View.GONE
            }
        }
    }

    class AssistantMessageViewHolder(private val binding: ItemAssistantMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun bind(message: ChatMessageEntity) {
            binding.tvMessage.text = message.content
            binding.tvTimestamp.text = timeFormat.format(Date(message.timestamp))
        }
    }

    class TypingViewHolder(binding: ItemTypingBinding) : RecyclerView.ViewHolder(binding.root)

    class DiffCallback : DiffUtil.ItemCallback<ChatMessageEntity>() {
        override fun areItemsTheSame(oldItem: ChatMessageEntity, newItem: ChatMessageEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessageEntity, newItem: ChatMessageEntity): Boolean =
            oldItem == newItem
    }
}
