package com.swarn.chatapp.domain.websocket

import com.swarn.chatapp.domain.model.ChatBot
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface WebSocketClient {
    val connectionStateFlow: StateFlow<ConnectionState>
    fun connect()
    fun disconnect()
    fun sendMessage(message: ChatMessage)
    fun observeMessages(): Flow<ChatMessage>
    fun observeConnectionState(): Flow<ConnectionState>
    fun observeMessageStatus(): Flow<Pair<String, MessageStatus>>
    fun registerBot(bot: ChatBot): Flow<Unit>
}

enum class ConnectionState {
    Connected,
    Connecting,
    Disconnected,
    Error,
    ReconnectingBackoff,  // When attempting reconnection with backoff
    NetworkUnavailable,   // When network is not available
} 