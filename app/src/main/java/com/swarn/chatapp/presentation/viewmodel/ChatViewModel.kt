package com.swarn.chatapp.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.MessageStatus
import com.swarn.chatapp.domain.repository.ChatRepository
import com.swarn.chatapp.domain.websocket.ConnectionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isOnline: Boolean = true,
    val error: String? = null,
    val isLoading: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Disconnected
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val botId: String = savedStateHandle.get<String>("botId") ?: "bot1"
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private val messageIds = mutableSetOf<String>()

    init {
        Log.d("ChatViewModel", "Initializing ChatViewModel for bot $botId")
        observeMessages()
        observeNetworkState()
        observeIncomingMessages()
        observeConnectionState()
        observeMessageStatus()
        markMessagesAsRead()
    }

    private fun markMessagesAsRead() {
        viewModelScope.launch {
            _uiState.value.messages.forEach { message ->
                if (!message.isFromUser && !message.isRead) {
                    chatRepository.markMessageAsRead(message.id)
                }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            chatRepository.getMessagesForBot(botId)
                .collect { messages ->
                    _uiState.update { it.copy(messages = messages) }
                }
        }
    }

    private fun observeNetworkState() {
        viewModelScope.launch {
            chatRepository.observeNetworkState()
                .collect { isOnline ->
                    _uiState.update { it.copy(isOnline = isOnline) }
                }
        }
    }

    private fun observeIncomingMessages() {
        viewModelScope.launch {
            chatRepository.observeMessages()
                .filter { it.botId == botId }
                .collect { message ->
                    _uiState.update { state ->
                        val updatedMessages = (state.messages + message)
                            .distinctBy { it.id }
                            .sortedBy { it.timestamp }
                        state.copy(messages = updatedMessages)
                    }
                }
        }
    }

    fun observeConnectionState() {
        viewModelScope.launch {
            chatRepository.observeConnectionState()
                .collect { state ->
                    _uiState.update { it.copy(connectionState = state) }
                }
        }
    }

    private fun observeMessageStatus() {
        viewModelScope.launch {
            chatRepository.observeMessageStatus()
                .collect { (messageId, status) ->
                    _uiState.update { state ->
                        val updatedMessages = state.messages.map { message ->
                            if (message.id == messageId) {
                                message.copy(status = status)
                            } else {
                                message
                            }
                        }
                        state.copy(messages = updatedMessages)
                    }
                }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            try {
                Log.d("ChatViewModel", "Sending message: $content")
                val message = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    botId = botId,
                    content = content.trim(),
                    timestamp = System.currentTimeMillis(),
                    isFromUser = true,
                    isRead = true,
                    isQueued = false,
                    status = if (uiState.value.isOnline) MessageStatus.SENDING else MessageStatus.QUEUED
                )

                _uiState.update { state ->
                    val updatedMessages = (state.messages + message)
                        .distinctBy { it.id }
                        .sortedBy { it.timestamp }
                    state.copy(
                        messages = updatedMessages,
                        error = null
                    )
                }
                messageIds.add(message.id)
                Log.d("ChatViewModel", "Added message to UI state for optimistic update")

                chatRepository.sendMessage(message)
                Log.d("ChatViewModel", "Message sent through repository")
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error sending message: ${e.message}", e)
                _uiState.update { state ->
                    state.copy(error = "Failed to send message: ${e.message}")
                }
            }
        }
    }

    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            try {
                chatRepository.markMessageAsRead(messageId)
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Error marking message as read: ${e.message}", e)
                _uiState.update { state ->
                    state.copy(error = "Failed to mark message as read: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        messageIds.clear()
    }
}