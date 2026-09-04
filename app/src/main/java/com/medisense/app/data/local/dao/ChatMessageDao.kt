package com.medisense.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medisense.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId AND userId = :userId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: Long, userId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId AND userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesSync(conversationId: Long, userId: String, limit: Int = 8): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun deleteMessagesForConversation(conversationId: Long, userId: String)

    @Query("DELETE FROM chat_messages WHERE userId = :userId")
    suspend fun deleteAllMessagesForUser(userId: String)
}
