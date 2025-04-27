package com.swarn.chatapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarn.chatapp.domain.model.ChatBot
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.ConversationPreview
import com.swarn.chatapp.domain.repository.ChatRepository
import com.swarn.chatapp.domain.websocket.WebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val webSocketClient: WebSocketClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    private var selectedBotId: String? = null

    init {
        observeConversationPreviews()
        observeNetworkState()
        observeMessageStatus()
    }

    private fun observeConversationPreviews() {
        viewModelScope.launch {
            repository.getConversationPreviews()
                .collect { previews ->
                    _uiState.update { it.copy(conversationPreviews = previews) }
                }
        }
    }

    private fun observeNetworkState() {
        viewModelScope.launch {
            repository.observeNetworkState()
                .collect { isOnline ->
                    _uiState.update { it.copy(isOnline = isOnline) }
                }
        }
    }

    private fun observeMessageStatus() {
        viewModelScope.launch {
            repository.observeMessageStatus()
                .collect { (messageId, status) ->
                    // Update the preview status for the message
                    _uiState.update { state ->
                        val updatedPreviews = state.conversationPreviews.map { preview ->
                            if (preview.lastMessageId == messageId) {
                                preview.copy(messageStatus = status)
                            } else {
                                preview
                            }
                        }
                        state.copy(conversationPreviews = updatedPreviews)
                    }
                }
        }
    }

    fun selectBot(botId: String) {
        selectedBotId = botId
        _uiState.update { it.copy(selectedBotId = botId) }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || selectedBotId == null) return

        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            botId = selectedBotId!!,
            content = content,
            timestamp = System.currentTimeMillis(),
            isFromUser = true,
            isRead = false,
            isQueued = false
        )

        viewModelScope.launch {
            try {
                repository.sendMessage(message)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to send message: ${e.message}") }
            }
        }
    }

    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            try {
                repository.markMessageAsRead(messageId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to mark message as read: ${e.message}") }
            }
        }
    }

    fun createBot(name: String) {
        val newBot = ChatBot(
            id = UUID.randomUUID().toString(),
            name = name,
            avatarUrl = null
        )
        
        viewModelScope.launch {
            try {
                // Register the bot with the WebSocket server
                webSocketClient.registerBot(newBot).collect()
                // Register the bot with the repository
                repository.registerBot(newBot)
                selectedBotId = newBot.id
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to create bot: ${e.message}") }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            repository.clearAllMessages()
        }
    }
}

data class ChatListUiState(
    val bots: List<ChatBot> = emptyList(),
    val conversationPreviews: List<ConversationPreview> = emptyList(),
    val selectedBotId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isOnline: Boolean = true
) 