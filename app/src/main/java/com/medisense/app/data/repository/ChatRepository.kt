package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.ChatMessageDao
import com.medisense.app.data.remote.gemini.GeminiApiService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
    private val geminiApiService: GeminiApiService
) {
    suspend fun sendMessage(message: String): Result<String> {
        return Result.failure(Exception("Not implemented"))
    }

    fun getChatHistory(): Flow<List<Any>> {
        return emptyFlow()
    }
    
    suspend fun clearHistory() {}
}
