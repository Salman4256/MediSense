package com.medisense.app.data.repository

import com.medisense.app.data.local.dao.ChatMessageDao
import com.medisense.app.data.local.entity.ChatMessageEntity
import com.medisense.app.data.remote.gemini.GeminiApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.medisense.app.data.local.dao.PredictionDao
import com.medisense.app.data.local.dao.ExplanationDao
import com.medisense.app.data.local.dao.HealthProfileDao

interface ChatRepository {
    fun getChatHistory(sessionId: String): Flow<List<ChatMessageEntity>>
    fun getRecentSessions(): Flow<List<ChatMessageEntity>>
    suspend fun sendMessage(sessionId: String, message: String): Result<String>
    suspend fun deleteSession(sessionId: String)
    suspend fun clearHistory()
}

class ChatRepositoryImpl @Inject constructor(
    private val chatMessageDao: ChatMessageDao,
    private val predictionDao: PredictionDao,
    private val explanationDao: ExplanationDao,
    private val healthProfileDao: HealthProfileDao,
    private val geminiApiService: GeminiApiService
) : ChatRepository {

    override fun getChatHistory(sessionId: String): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getMessagesForSession(sessionId)
    }

    override fun getRecentSessions(): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getRecentSessions()
    }

    override suspend fun sendMessage(sessionId: String, message: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Save user message locally
            val userEntity = ChatMessageEntity(
                sessionId = sessionId,
                sender = "USER",
                message = message,
                isSynced = false
            )
            chatMessageDao.insertMessage(userEntity)

            // 2. Fetch history for Gemini (limit to 20 messages)
            val snapshot = chatMessageDao.getSnapshotMessagesForSession(sessionId, 20)
            
            // Reverse to chronological order (since we queried DESC)
            // Exclude the current user message which is the most recent one (index 0)
            val history = snapshot.drop(1)
                .reversed()
                .map { entity ->
                    val role = if (entity.sender == "USER") "user" else "model"
                    Pair(role, entity.message)
                }

            // 3. Collect Smart Health Context
            val userId = "mock-supabase-user-id" // Matching HealthProfileRepository
            val profile = healthProfileDao.getHealthProfileSnapshot(userId)
            val latestPrediction = predictionDao.getLatestPredictionSnapshot(userId)
            
            val contextBuilder = java.lang.StringBuilder()
            
            if (profile != null) {
                contextBuilder.append("Health Profile:\n")
                if (profile.dateOfBirth.isNotEmpty()) contextBuilder.append("DOB: ${profile.dateOfBirth}\n")
                if (profile.gender.isNotEmpty()) contextBuilder.append("Gender: ${profile.gender}\n")
                if (profile.bloodGroup.isNotEmpty()) contextBuilder.append("Blood Group: ${profile.bloodGroup}\n")
                contextBuilder.append("\n")
            }

            if (latestPrediction != null) {
                contextBuilder.append("Prediction:\n")
                contextBuilder.append("${latestPrediction.topDisease} (${(latestPrediction.confidence * 100).toInt()}%)\n\n")
                
                contextBuilder.append("Symptoms:\n")
                val symptomsList = latestPrediction.selectedSymptoms.split(",")
                for (sym in symptomsList) {
                    if (sym.isNotBlank()) contextBuilder.append("${sym.trim()}\n")
                }
                contextBuilder.append("\n")
                
                val explanation = explanationDao.getExplanationSnapshotForPrediction(latestPrediction.id)
                if (explanation != null) {
                    contextBuilder.append("Explainable AI Result:\n")
                    contextBuilder.append("${explanation.explanationText}\n\n")
                }
            }

            val contextualMessage = if (contextBuilder.isNotEmpty()) {
                "${contextBuilder.toString().trim()}\n\nUser Question:\n$message"
            } else {
                message
            }

            val response = geminiApiService.sendMessage(history, contextualMessage)

            // 3. Save AI response locally
            val aiEntity = ChatMessageEntity(
                sessionId = sessionId,
                sender = "AI",
                message = response,
                isSynced = false
            )
            chatMessageDao.insertMessage(aiEntity)

            Result.success(response)
        } catch (e: Exception) {
            val errorMsg = "⚠️ I encountered an error: ${e.message ?: e.javaClass.simpleName}"
            val errorEntity = ChatMessageEntity(
                sessionId = sessionId,
                sender = "AI",
                message = errorMsg,
                isSynced = false
            )
            chatMessageDao.insertMessage(errorEntity)
            Result.failure(e)
        }
    }

    override suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        chatMessageDao.deleteSession(sessionId)
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        chatMessageDao.clearHistory()
    }
}
