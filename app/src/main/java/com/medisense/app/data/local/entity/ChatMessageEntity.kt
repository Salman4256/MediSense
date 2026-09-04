package com.medisense.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: Long,
    val userId: String,
    val role: String = "USER",
    val content: String = "",
    val imageUri: String? = null,
    val messageType: String = "TEXT",
    val timestamp: Long = System.currentTimeMillis()
)
