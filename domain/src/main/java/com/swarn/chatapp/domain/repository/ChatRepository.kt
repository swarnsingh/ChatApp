package com.swarn.chatapp.domain.repository

import com.swarn.chatapp.domain.model.ChatBot
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.ConversationPreview
import com.swarn.chatapp.domain.model.MessageStatus
import com.swarn.chatapp.domain.websocket.ConnectionState
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getConversationPreviews(): Flow<List<ConversationPreview>>
    fun getMessagesForBot(botId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(message: ChatMessage)
    suspend fun markMessageAsRead(messageId: String)
    fun observeNetworkState(): Flow<Boolean>
    fun observeMessages(): Flow<ChatMessage>
    fun observeConnectionState(): Flow<ConnectionState>
    fun observeMessageStatus(): Flow<Pair<String, MessageStatus>>
    suspend fun clearAllMessages()
    suspend fun registerBot(bot: ChatBot)
} 