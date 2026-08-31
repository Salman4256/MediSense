package com.medisense.app.data.repository

import com.medisense.app.ai.groq.GroqAiService
import com.medisense.app.ai.groq.GroqPromptBuilder
import com.medisense.app.data.local.dao.ChatMessageDao
import com.medisense.app.data.local.dao.ConversationDao
import com.medisense.app.data.local.dao.HealthProfileDao
import com.medisense.app.data.local.entity.ChatMessageEntity
import com.medisense.app.data.local.entity.ConversationEntity
import com.medisense.app.data.remote.supabase.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthAssistantRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val chatMessageDao: ChatMessageDao,
    private val healthProfileDao: HealthProfileDao,
    private val authService: AuthService,
    private val groqAiService: GroqAiService
) {

    private fun getCurrentUserId(): String = authService.getCurrentUserId() ?: "offline-user"

    /**
     * Observes all conversations for the authenticated user.
     */
    fun getConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getConversationsForUser(getCurrentUserId())
    }

    /**
     * Observes all messages in a specific conversation for the authenticated user.
     */
    fun getMessagesForConversation(conversationId: Long): Flow<List<ChatMessageEntity>> {
        return chatMessageDao.getMessagesForConversation(conversationId, getCurrentUserId())
    }

    /**
     * Creates a new conversation session with title generated from initial prompt.
     */
    suspend fun createConversation(firstPrompt: String = "New Health Chat"): Long = withContext(Dispatchers.IO) {
        val title = generateConversationTitle(firstPrompt)
        val conversation = ConversationEntity(
            userId = getCurrentUserId(),
            title = title,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        conversationDao.insertConversation(conversation)
    }

    /**
     * Sends user message (with optional image), requests AI response from Groq/Gemini, and stores both in Room.
     */
    suspend fun sendMessage(
        conversationId: Long,
        userPrompt: String,
        imageUri: String? = null,
        imageBase64: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val userId = getCurrentUserId()
        val promptText = userPrompt.trim().ifBlank { if (imageUri != null) "Describe and analyze this health image." else "" }

        // 1. Store User Message
        val userMessage = ChatMessageEntity(
            conversationId = conversationId,
            userId = userId,
            role = "USER",
            content = promptText,
            imageUri = imageUri,
            messageType = if (imageUri != null) "IMAGE_WITH_TEXT" else "TEXT",
            timestamp = System.currentTimeMillis()
        )
        chatMessageDao.insertMessage(userMessage)

        // 2. Update Conversation Timestamp and Title if default
        val conversation = conversationDao.getConversationById(conversationId, userId)
        if (conversation != null) {
            val updatedTitle = if (conversation.title == "New Health Chat") generateConversationTitle(promptText) else conversation.title
            conversationDao.updateConversation(
                conversation.copy(
                    title = updatedTitle,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // 3. Load Recent History Window and Health Profile
        val recentHistory = chatMessageDao.getRecentMessagesSync(conversationId, userId, limit = 8).reversed()
        val healthProfile = healthProfileDao.getHealthProfile(userId)

        // 4. Build Multimodal Payload
        val payload = GroqPromptBuilder.buildMessagesPayload(
            userPrompt = promptText,
            recentHistory = recentHistory.filter { it.id != userMessage.id },
            healthProfile = healthProfile,
            imageBase64 = imageBase64
        )

        // 5. Request Completion from Groq/Gemini AI Service
        val aiResult = groqAiService.getChatCompletion(payload)

        if (aiResult.isSuccess) {
            val aiResponseContent = aiResult.getOrThrow()
            // Store Assistant Message
            val assistantMessage = ChatMessageEntity(
                conversationId = conversationId,
                userId = userId,
                role = "ASSISTANT",
                content = aiResponseContent,
                timestamp = System.currentTimeMillis()
            )
            chatMessageDao.insertMessage(assistantMessage)
            Result.success(aiResponseContent)
        } else {
            Result.failure(aiResult.exceptionOrNull() ?: Exception("Failed to receive response from assistant."))
        }
    }

    /**
     * Deletes a conversation and all its messages.
     */
    suspend fun deleteConversation(conversationId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            chatMessageDao.deleteMessagesForConversation(conversationId, userId)
            conversationDao.deleteConversationById(conversationId, userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes all conversations and messages for current user.
     */
    suspend fun clearAllConversations(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val userId = getCurrentUserId()
            chatMessageDao.deleteAllMessagesForUser(userId)
            conversationDao.deleteAllConversationsForUser(userId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateConversationTitle(prompt: String): String {
        val clean = prompt.trim()
            .replace("\n", " ")
            .replace(Regex("[^a-zA-Z0-9 ?,-]"), "")
        return if (clean.length > 36) clean.take(36) + "..." else clean.ifBlank { "Health Discussion" }
    }
}
