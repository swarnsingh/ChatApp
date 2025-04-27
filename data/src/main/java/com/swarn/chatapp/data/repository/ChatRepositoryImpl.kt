package com.swarn.chatapp.data.repository

import android.util.Log
import com.swarn.chatapp.data.remote.NetworkStateMonitor
import com.swarn.chatapp.domain.model.ChatBot
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.ConversationPreview
import com.swarn.chatapp.domain.model.MessageStatus
import com.swarn.chatapp.domain.repository.ChatRepository
import com.swarn.chatapp.domain.websocket.ConnectionState
import com.swarn.chatapp.domain.websocket.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val webSocketClient: WebSocketClient,
    private val networkStateMonitor: NetworkStateMonitor,
) : ChatRepository {

    companion object {
        private const val MAX_MESSAGES_PER_CONVERSATION = 100
        private const val MAX_QUEUED_MESSAGES = 50
    }

    private val conversations = mutableMapOf<String, MutableList<ChatMessage>>()
    private val _conversationPreviews = MutableStateFlow<List<ConversationPreview>>(emptyList())
    private val registeredBots = mutableMapOf<String, ChatBot>()
    private val queuedMessages = mutableListOf<ChatMessage>()
    private val sentMessageIds = mutableSetOf<String>()
    private var nextBotId = 1
    private var lastSyncTimestamp = 0L
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messageStatusLock = Any()

    init {
        networkStateMonitor.observe()
            .onEach { isOnline ->
                if (isOnline) {
                    webSocketClient.connect()
                    sendQueuedMessages()
                } else {
                    webSocketClient.disconnect()
                }
            }
            .catch { e ->
                Log.e("ChatRepositoryImpl", "Error in network state observation: ${e.message}", e)
            }
            .launchIn(CoroutineScope(Dispatchers.IO))

        webSocketClient.connectionStateFlow
            .onEach { state ->
                if (state == ConnectionState.Connected) {
                    sendQueuedMessages()
                }
            }
            .catch { e ->
                Log.e("ChatRepositoryImpl", "Error observing connection state: ${e.message}", e)
            }
            .launchIn(CoroutineScope(Dispatchers.IO))

        webSocketClient.observeMessages()
            .onEach { message ->
                val botMessages = conversations.getOrPut(message.botId) { mutableListOf() }
                val insertIndex = botMessages.indexOfFirst { it.timestamp > message.timestamp }
                if (insertIndex == -1) {
                    botMessages.add(message)
                } else {
                    botMessages.add(insertIndex, message)
                }
                updateConversationPreviews()
            }
            .catch { e ->
                Log.e("ChatRepositoryImpl", "Error observing messages: ${e.message}", e)
            }
            .launchIn(CoroutineScope(Dispatchers.IO))

        webSocketClient.observeMessageStatus()
            .onEach { (messageId, status) ->
                updateMessageStatus(messageId, status)
            }
            .catch { e ->
                Log.e("ChatRepositoryImpl", "Error observing message status: ${e.message}", e)
            }
            .launchIn(CoroutineScope(Dispatchers.IO))
    }

    private fun sendQueuedMessages() {
        if (queuedMessages.isEmpty()) return
        
        coroutineScope.launch {
            val messagesToSend = synchronized(messageStatusLock) {
                queuedMessages.toList().also { queuedMessages.clear() }
            }
            
            for (message in messagesToSend) {
                try {
                    if (sentMessageIds.contains(message.id)) continue

                    updateMessageStatus(message.id, MessageStatus.SENDING)
                    webSocketClient.sendMessage(message)
                    sentMessageIds.add(message.id)
                    updateMessageStatus(message.id, MessageStatus.SENT)
                } catch (e: Exception) {
                    Log.e("ChatRepositoryImpl", "Failed to send queued message: ${e.message}")
                    updateMessageStatus(message.id, MessageStatus.ERROR)
                    synchronized(messageStatusLock) {
                        queuedMessages.add(message)
                    }
                    updateMessageStatus(message.id, MessageStatus.QUEUED)
                }
            }
        }
    }

    override fun getConversationPreviews(): Flow<List<ConversationPreview>> = _conversationPreviews

    override fun getMessagesForBot(botId: String): Flow<List<ChatMessage>> = flow {
        val messages = conversations[botId] ?: emptyList()
        val uniqueMessages = messages.distinctBy { it.id }
            .sortedBy { it.timestamp }
            .takeLast(MAX_MESSAGES_PER_CONVERSATION)
        emit(uniqueMessages)
    }

    override suspend fun sendMessage(message: ChatMessage) {
        try {
            if (sentMessageIds.contains(message.id)) return

            val queuedMessage = message.copy(status = MessageStatus.QUEUED)

            synchronized(messageStatusLock) {
                val botMessages = conversations.getOrPut(queuedMessage.botId) { mutableListOf() }
                val insertIndex = botMessages.indexOfFirst { it.timestamp > queuedMessage.timestamp }
                if (insertIndex == -1) {
                    botMessages.add(queuedMessage)
                } else {
                    botMessages.add(insertIndex, queuedMessage)
                }
                updateConversationPreviews()
            }

            val isOnline = networkStateMonitor.observe().first()
            if (isOnline) {
                updateMessageStatus(queuedMessage.id, MessageStatus.SENDING)
                try {
                    webSocketClient.sendMessage(queuedMessage)
                    sentMessageIds.add(queuedMessage.id)
                    updateMessageStatus(queuedMessage.id, MessageStatus.SENT)
                } catch (e: Exception) {
                    Log.e("ChatRepositoryImpl", "Error sending message: ${e.message}", e)
                    updateMessageStatus(queuedMessage.id, MessageStatus.ERROR)
                    synchronized(messageStatusLock) {
                        if (queuedMessages.size < MAX_QUEUED_MESSAGES) {
                            queuedMessages.add(queuedMessage)
                            updateMessageStatus(queuedMessage.id, MessageStatus.QUEUED)
                        }
                    }
                    throw e
                }
            } else {
                synchronized(messageStatusLock) {
                    if (queuedMessages.size < MAX_QUEUED_MESSAGES) {
                        queuedMessages.add(queuedMessage)
                    } else {
                        queuedMessages.removeAt(0)
                        queuedMessages.add(queuedMessage)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatRepositoryImpl", "Error sending message: ${e.message}", e)
            updateMessageStatus(message.id, MessageStatus.ERROR)
            throw e
        }
    }

    private fun updateMessageStatus(messageId: String, status: MessageStatus) {
        synchronized(messageStatusLock) {
            conversations.values.forEach { messages ->
                messages.find { it.id == messageId }?.let { message ->
                    val index = messages.indexOf(message)
                    if (index != -1) {
                        messages[index] = message.copy(status = status)
                    }
                }
            }
            _conversationPreviews.value = _conversationPreviews.value.toList()
            
            conversations.forEach { (botId, messages) ->
                if (messages.any { it.id == messageId }) {
                    val updatedMessages = messages.toList()
                    conversations[botId] = updatedMessages.toMutableList()
                }
            }
        }
    }

    override suspend fun markMessageAsRead(messageId: String) {
        conversations.values.forEach { messages ->
            messages.find { it.id == messageId }?.let { message ->
                val index = messages.indexOf(message)
                if (index != -1) {
                    messages[index] = message.copy(isRead = true)
                }
            }
        }
        updateConversationPreviews()
    }

    override fun observeNetworkState(): Flow<Boolean> = networkStateMonitor.observe()

    override fun observeMessages(): Flow<ChatMessage> = webSocketClient.observeMessages()

    override fun observeConnectionState(): Flow<ConnectionState> = webSocketClient.connectionStateFlow

    override fun observeMessageStatus(): Flow<Pair<String, MessageStatus>> = webSocketClient.observeMessageStatus()

    override suspend fun clearAllMessages() {
        conversations.clear()
        _conversationPreviews.value = emptyList()
        sentMessageIds.clear()
        queuedMessages.clear()
        lastSyncTimestamp = 0L
    }

    override suspend fun registerBot(bot: ChatBot) {
        try {
            val botId = nextBotId.toString()
            nextBotId++
            
            val newBot = bot.copy(id = botId)
            
            webSocketClient.registerBot(newBot).collect { }
            
            registeredBots[botId] = newBot
            conversations.getOrPut(botId) { mutableListOf() }
            updateConversationPreviews()
        } catch (e: Exception) {
            Log.e("ChatRepositoryImpl", "Error registering bot: ${e.message}", e)
            throw e
        }
    }

    private fun updateConversationPreviews() {
        val previews = conversations.map { (botId, messages) ->
            val lastMessage = messages.lastOrNull()
            val bot = registeredBots[botId] ?: ChatBot(id = botId, name = "Bot $botId")
            val unreadCount = messages.count { !it.isRead && !it.isFromUser }
            ConversationPreview(
                id = botId,
                botName = bot.name,
                lastMessage = lastMessage?.content ?: "",
                lastMessageId = lastMessage?.id ?: "",
                lastMessageIsFromUser = lastMessage?.isFromUser ?: false,
                timestamp = lastMessage?.timestamp ?: System.currentTimeMillis(),
                isUnread = unreadCount > 0,
                bot = bot,
                messageStatus = lastMessage?.status ?: MessageStatus.SENT
            )
        }.sortedByDescending { it.timestamp }

        _conversationPreviews.value = previews
    }
} 