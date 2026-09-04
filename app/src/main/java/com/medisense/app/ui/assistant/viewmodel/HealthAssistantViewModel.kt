package com.medisense.app.ui.assistant.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medisense.app.data.local.entity.ChatMessageEntity
import com.medisense.app.data.local.entity.ConversationEntity
import com.medisense.app.data.repository.HealthAssistantRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ChatUiState {
    data class Success(val messages: List<ChatMessageEntity>, val isTyping: Boolean = false) : ChatUiState()
    object Loading : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

@HiltViewModel
class HealthAssistantViewModel @Inject constructor(
    private val repository: HealthAssistantRepository
) : ViewModel() {

    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _chatState = MutableStateFlow<ChatUiState>(ChatUiState.Success(emptyList()))
    val chatState: StateFlow<ChatUiState> = _chatState.asStateFlow()

    private val _conversations = MutableStateFlow<List<ConversationEntity>>(emptyList())
    val conversations: StateFlow<List<ConversationEntity>> = _conversations.asStateFlow()

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage.asSharedFlow()

    private var isSending = false

    init {
        loadConversations()
    }

    fun loadConversations() {
        viewModelScope.launch {
            repository.getConversations()
                .catch { e -> _errorMessage.emit(e.message ?: "Failed to load chats") }
                .collect { list ->
                    _conversations.value = list
                }
        }
    }

    fun selectConversation(conversationId: Long) {
        _currentConversationId.value = conversationId
        observeMessages(conversationId)
    }

    fun startNewConversation() {
        _currentConversationId.value = null
        _chatState.value = ChatUiState.Success(emptyList())
    }

    private fun observeMessages(conversationId: Long) {
        viewModelScope.launch {
            repository.getMessagesForConversation(conversationId)
                .catch { e -> _chatState.value = ChatUiState.Error(e.message ?: "Failed to load messages") }
                .collect { messages ->
                    _chatState.value = ChatUiState.Success(messages, isTyping = isSending)
                }
        }
    }

    fun sendMessage(prompt: String, imageUri: String? = null, imageBase64: String? = null) {
        if (prompt.isBlank() && imageBase64 == null) return
        if (isSending) return

        viewModelScope.launch {
            isSending = true
            var convId = _currentConversationId.value
            if (convId == null) {
                convId = repository.createConversation(prompt)
                _currentConversationId.value = convId
                observeMessages(convId)
            } else {
                val currentMessages = (_chatState.value as? ChatUiState.Success)?.messages ?: emptyList()
                _chatState.value = ChatUiState.Success(currentMessages, isTyping = true)
            }

            val result = repository.sendMessage(
                conversationId = convId,
                userPrompt = prompt,
                imageUri = imageUri,
                imageBase64 = imageBase64
            )

            isSending = false
            val currentMessages = (_chatState.value as? ChatUiState.Success)?.messages ?: emptyList()
            _chatState.value = ChatUiState.Success(currentMessages, isTyping = false)

            if (result.isFailure) {
                _errorMessage.emit(result.exceptionOrNull()?.message ?: "Failed to get AI response")
            }
        }
    }

    fun deleteConversation(conversationId: Long) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
            if (_currentConversationId.value == conversationId) {
                startNewConversation()
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            repository.clearAllConversations()
            startNewConversation()
        }
    }
}
