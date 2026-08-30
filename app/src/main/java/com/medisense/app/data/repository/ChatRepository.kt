package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.ChatMessageDao
import com.medisense.app.data.local.entity.ChatMessageEntity
import com.medisense.app.data.remote.gemini.GeminiApiService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

interface ChatRepository {
    fun getChatHistory(sessionId: String): Flow<List<ChatMessageEntity>>
    fun getRecentSessions(): Flow<List<ChatMessageEntity>>
    suspend fun sendMessage(sessionId: String, message: String): Result<String>
    suspend fun deleteSession(sessionId: String)
    suspend fun clearHistory()
}

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
    private val geminiApiService: GeminiApiService
) : ChatRepository {

    override fun getChatHistory(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getMessagesForSession(sessionId)
    }

    override fun getRecentSessions(): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getRecentSessions()
    }

    override suspend fun sendMessage(sessionId: String, message: String): Result<String> {
        return try {
            // Save user message
            val userEntity = ChatMessageEntity(
                sessionId = sessionId,
                sender = "USER",
                message = message,
                timestamp = System.currentTimeMillis()
            )
            chatMessageDao.insertMessage(userEntity)

            // Fetch previous history for context
            val historySnapshot = chatMessageDao.getSnapshotMessagesForSession(sessionId, limit = 10)
                .reversed()
                .map { Pair(it.sender, it.message) }

            // Query AI
            val reply = geminiApiService.generateResponse(message, historySnapshot)

            // Save AI reply
            val aiEntity = ChatMessageEntity(
                sessionId = sessionId,
                sender = "AI",
                message = reply,
                timestamp = System.currentTimeMillis()
            )
            chatMessageDao.insertMessage(aiEntity)

            Result.success(reply)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        chatMessageDao.deleteSession(sessionId)
    }

    override suspend fun clearHistory() {
        chatMessageDao.clearHistory()
    }
}
