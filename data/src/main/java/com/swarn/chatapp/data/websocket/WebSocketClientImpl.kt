package com.swarn.chatapp.data.websocket

import android.util.Log
import com.piesocket.channels.Channel
import com.piesocket.channels.PieSocket
import com.piesocket.channels.misc.PieSocketEvent
import com.piesocket.channels.misc.PieSocketEventListener
import com.piesocket.channels.misc.PieSocketOptions
import com.swarn.chatapp.data.remote.ApiKeys
import com.swarn.chatapp.data.remote.NetworkStateMonitor
import com.swarn.chatapp.domain.model.ChatBot
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.MessageStatus
import com.swarn.chatapp.domain.websocket.ConnectionState
import com.swarn.chatapp.domain.websocket.WebSocketClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WebSocketClientImpl"
private const val MAX_RECONNECT_ATTEMPTS = 3
private const val RECONNECT_DELAY_MS = 3000L
private const val ROOM_ID = "1"
private const val MAX_RETRY_DELAY_MS = 10000L
private const val MAX_QUEUED_MESSAGES = 20
private const val MAX_MESSAGE_SIZE_BYTES = 1024 * 1024

data class QueuedMessage(
    val message: ChatMessage,
    val timestamp: Long = System.currentTimeMillis(),
)

@Singleton
class WebSocketClientImpl @Inject constructor(
    private val apiKeys: ApiKeys,
    private val networkStateMonitor: NetworkStateMonitor,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WebSocketClient {

    private val coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _connectionStateFlow =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionStateFlow: StateFlow<ConnectionState> =
        _connectionStateFlow.asStateFlow()

    override fun observeConnectionState(): Flow<ConnectionState> =
        _connectionStateFlow.asStateFlow()

    private val messageFlow = MutableSharedFlow<ChatMessage>()
    private val messageStatusFlow = MutableSharedFlow<Pair<String, MessageStatus>>()
    private val queuedMessages = ArrayDeque<QueuedMessage>()
    private val retryQueue = ArrayDeque<QueuedMessage>()
    private var isRetrying = false
    private var piesocket: PieSocket? = null
    private var mainChannel: Channel? = null
    private var messageListener: PieSocketEventListener? = null
    private var connectionStateListener: PieSocketEventListener? = null
    private var currentRetryJob: Job? = null
    private var _isConnected = false
    private var isReconnecting = false
    private var reconnectAttempts = 0
    private val sentMessageIds = mutableSetOf<String>()

    override fun connect() {
        if (_isConnected || isReconnecting) {
            Log.d(TAG, "Already connected or reconnecting, skipping connection")
            return
        }

        coroutineScope.launch {
            if (!networkStateMonitor.observe().first()) {
                Log.d(TAG, "No internet connection available, skipping connection attempt")
                _connectionStateFlow.tryEmit(ConnectionState.NetworkUnavailable)
                return@launch
            }

            try {
                cleanupConnection()

                isReconnecting = true
                _connectionStateFlow.tryEmit(ConnectionState.Connecting)

                val options = PieSocketOptions().apply {
                    apiKey = apiKeys.getPieSocketApiKey()
                    clusterId = apiKeys.getPieSocketClusterId()
                    setNotifySelf(true)
                    enableLogs = true
                }

                Log.d(
                    TAG,
                    "Initializing PieSocket with cluster: ${apiKeys.getPieSocketClusterId()}"
                )
                piesocket = PieSocket(options)
                mainChannel = piesocket?.join(ROOM_ID)

                messageListener = object : PieSocketEventListener() {
                    override fun handleEvent(event: PieSocketEvent) {
                        try {
                            Log.d(TAG, "Message event received: ${event.data}")
                            handleIncomingMessage(event)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling message event: ${e.message}", e)
                        }
                    }
                }

                connectionStateListener = object : PieSocketEventListener() {
                    override fun handleEvent(event: PieSocketEvent) {
                        try {
                            Log.d(TAG, "Connection state event received: ${event.data}")
                            handleConnectionStateEvent(event)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling connection state event: ${e.message}", e)
                            handleConnectionError(e)
                        }
                    }
                }

                mainChannel?.listen("message", messageListener!!)
                mainChannel?.listen("connection_state", connectionStateListener!!)

                _isConnected = true
                isReconnecting = false
                _connectionStateFlow.tryEmit(ConnectionState.Connected)
                Log.d(TAG, "PieSocket initialized and connected")
                sendQueuedMessages()
            } catch (e: Exception) {
                Log.e(TAG, "Connection error: ${e.message}", e)
                handleConnectionError(e)
            }
        }
    }

    private fun handleConnectionStateEvent(event: PieSocketEvent) {
        when (event.data?.toString()) {
            "connected" -> {
                _isConnected = true
                isReconnecting = false
                reconnectAttempts = 0
                currentRetryJob?.cancel()
                _connectionStateFlow.tryEmit(ConnectionState.Connected)
                Log.d(TAG, "PieSocket connected successfully")
                sendQueuedMessages()
            }

            "disconnected" -> {
                _isConnected = false
                _connectionStateFlow.tryEmit(ConnectionState.Disconnected)
                Log.d(TAG, "PieSocket disconnected")

                coroutineScope.launch {
                    if (!networkStateMonitor.observe().first()) {
                        Log.e(TAG, "No internet connection")
                        _connectionStateFlow.tryEmit(ConnectionState.NetworkUnavailable)
                        cleanupConnection()
                        queuedMessages.clear()
                    } else if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
                        scheduleReconnect()
                    } else {
                        Log.e(TAG, "Max reconnection attempts reached")
                        _connectionStateFlow.tryEmit(ConnectionState.Error)
                        cleanupConnection()
                    }
                }
            }

            "error" -> {
                handleConnectionError(Exception(event.data?.toString() ?: "Unknown error"))
            }

            "reconnecting" -> {
                _connectionStateFlow.tryEmit(ConnectionState.ReconnectingBackoff)
                Log.d(TAG, "PieSocket reconnecting...")
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnection attempts reached")
            _connectionStateFlow.tryEmit(ConnectionState.Error)
            cleanupConnection()
            return
        }

        reconnectAttempts++
        val delay = minOf(RECONNECT_DELAY_MS * (1 shl (reconnectAttempts - 1)), MAX_RETRY_DELAY_MS)
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts after ${delay}ms")

        _connectionStateFlow.tryEmit(ConnectionState.ReconnectingBackoff)

        currentRetryJob?.cancel()
        currentRetryJob = coroutineScope.launch {
            try {
                delay(delay)
                if (networkStateMonitor.observe().first()) {
                    connect()
                } else {
                    Log.e(TAG, "No internet connection available")
                    _connectionStateFlow.tryEmit(ConnectionState.NetworkUnavailable)
                    cleanupConnection()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reconnection error: ${e.message}", e)
                handleConnectionError(e)
            }
        }
    }

    private fun handleConnectionError(error: Exception) {
        Log.e(TAG, "Connection error: ${error.message}")

        coroutineScope.launch {
            if (!networkStateMonitor.observe().first()) {
                _connectionStateFlow.tryEmit(ConnectionState.NetworkUnavailable)
            } else {
                _connectionStateFlow.tryEmit(ConnectionState.Error)
            }
            cleanupConnection()
        }
    }

    private fun cleanupConnection() {
        try {
            currentRetryJob?.cancel()
            currentRetryJob = null

            coroutineScope.coroutineContext.cancelChildren()

            messageListener = null
            connectionStateListener = null

            mainChannel?.disconnect()
            mainChannel = null

            piesocket?.leave(ROOM_ID)
            piesocket = null

            _isConnected = false
            isReconnecting = false
            reconnectAttempts = 0
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }

    override fun disconnect() {
        cleanupConnection()
    }

    override fun observeMessages(): Flow<ChatMessage> = messageFlow

    override fun sendMessage(message: ChatMessage) {
        if (message.toString().length > MAX_MESSAGE_SIZE_BYTES) {
            Log.e(TAG, "Message exceeds maximum size limit")
            return
        }

        if (sentMessageIds.contains(message.id)) {
            Log.d(TAG, "Message already sent, skipping: ${message.id}")
            return
        }

        if (!_isConnected) {
            if (queuedMessages.size < MAX_QUEUED_MESSAGES) {
                queuedMessages.addLast(QueuedMessage(message))
                coroutineScope.launch {
                    messageStatusFlow.emit(message.id to MessageStatus.QUEUED)
                }
                Log.d(TAG, "Message queued. Queue size: ${queuedMessages.size}")
            } else {
                Log.w(TAG, "Message queue full, dropping oldest message")
                queuedMessages.removeFirst()
                queuedMessages.addLast(QueuedMessage(message))
                coroutineScope.launch {
                    messageStatusFlow.emit(message.id to MessageStatus.QUEUED)
                }
            }
            return
        }

        try {
            coroutineScope.launch {
                messageStatusFlow.emit(message.id to MessageStatus.SENDING)
            }

            val event = PieSocketEvent("message").apply {
                data = JSONObject().apply {
                    put("id", message.id)
                    put("event", "message")
                    put("data", message.content)
                    put("isFromUser", true)
                    put("botId", message.botId)
                    put("timestamp", message.timestamp)
                }.toString()
            }
            mainChannel?.publish(event)
            sentMessageIds.add(message.id)

            coroutineScope.launch {
                messageStatusFlow.emit(message.id to MessageStatus.SENT)
                Log.d(TAG, "Message sent successfully: ${message.id}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Send message error: ${e.message}")
            coroutineScope.launch {
                messageStatusFlow.emit(message.id to MessageStatus.ERROR)

                if (networkStateMonitor.observe()
                        .first() && queuedMessages.size < MAX_QUEUED_MESSAGES
                ) {
                    queuedMessages.addLast(QueuedMessage(message))
                    messageStatusFlow.emit(message.id to MessageStatus.QUEUED)
                    startRetryProcess()
                }
            }
            throw e
        }
    }

    override fun registerBot(bot: ChatBot): Flow<Unit> = flow {
        ensureConnected()

        try {
            val event = PieSocketEvent("register_bot").apply {
                data = JSONObject().apply {
                    put("event", "register_bot")
                    put("botId", bot.id)
                    put("botName", bot.name)
                    put("timestamp", System.currentTimeMillis())
                }.toString()
            }
            mainChannel?.publish(event)
            Log.d(TAG, "Bot registration sent: ${event.data}")
            emit(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Bot registration error: ${e.localizedMessage}", e)
            throw e
        }
    }

    private fun handleIncomingMessage(event: PieSocketEvent) {
        try {
            val rawMessage = event.data?.toString().orEmpty()
            Log.d(TAG, "Raw message received: $rawMessage")

            val json = JSONObject(rawMessage)
            val eventType = json.optString("event", "")
            val messageId = json.optString("id", UUID.randomUUID().toString())

            if (sentMessageIds.contains(messageId)) {
                Log.d(TAG, "Duplicate message received, skipping: $messageId")
                return
            }

            when (eventType) {
                "message" -> {
                    val content = json.optString("data", "")
                    val botId = json.optString("botId", "bot1")
                    val isFromUser = json.optBoolean("isFromUser", false)
                    val timestamp = json.optLong("timestamp", System.currentTimeMillis())

                    Log.d(
                        TAG,
                        "Processing message - Content: $content, BotId: $botId, IsFromUser: $isFromUser"
                    )

                    val chatMessage = ChatMessage(
                        id = messageId,
                        botId = botId,
                        content = content,
                        timestamp = timestamp,
                        isFromUser = isFromUser,
                        isRead = false,
                        isQueued = false,
                        status = if (isFromUser) MessageStatus.SENT else MessageStatus.UNREAD
                    )

                    Log.d(TAG, "Emitting message to flow: $chatMessage")
                    coroutineScope.launch {
                        try {
                            messageFlow.emit(chatMessage)
                            sentMessageIds.add(messageId)
                            Log.d(TAG, "Message emitted successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error emitting message to flow: ${e.message}", e)
                        }
                    }
                }

                "register_bot" -> {
                    val botId = json.optString("botId", "")
                    val botName = json.optString("botName", "")
                    Log.d(TAG, "Bot registered: $botName (ID: $botId)")
                }

                else -> {
                    Log.d(TAG, "Unknown event type received: $eventType")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process message: ${e.localizedMessage}", e)
        }
    }

    private fun ensureConnected() {
        if (!_isConnected) {
            connect()
        }
    }

    private fun sendQueuedMessages() {
        if (queuedMessages.isEmpty()) {
            Log.d(TAG, "No queued messages to send")
            return
        }

        Log.d(TAG, "Sending ${queuedMessages.size} queued messages")

        coroutineScope.launch {
            while (queuedMessages.isNotEmpty()) {
                val queuedMessage = queuedMessages.removeFirst()
                try {
                    if (sentMessageIds.contains(queuedMessage.message.id)) {
                        Log.d(
                            TAG,
                            "Skipping already sent queued message: ${queuedMessage.message.id}"
                        )
                        continue
                    }

                    messageStatusFlow.emit(queuedMessage.message.id to MessageStatus.SENDING)

                    val event = PieSocketEvent("message").apply {
                        data = JSONObject().apply {
                            put("id", queuedMessage.message.id)
                            put("event", "message")
                            put("data", queuedMessage.message.content)
                            put("isFromUser", true)
                            put("botId", queuedMessage.message.botId)
                            put("timestamp", queuedMessage.message.timestamp)
                        }.toString()
                    }
                    mainChannel?.publish(event)
                    sentMessageIds.add(queuedMessage.message.id)

                    messageStatusFlow.emit(queuedMessage.message.id to MessageStatus.SENT)
                    Log.d(TAG, "Successfully sent queued message: ${queuedMessage.message.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send queued message: ${e.message}")
                    messageStatusFlow.emit(queuedMessage.message.id to MessageStatus.ERROR)

                    retryQueue.addLast(queuedMessage)
                    messageStatusFlow.emit(queuedMessage.message.id to MessageStatus.QUEUED)
                    startRetryProcess()
                    break
                }
            }
        }
    }

    private fun startRetryProcess() {
        if (isRetrying) return

        isRetrying = true
        coroutineScope.launch {
            try {
                while (retryQueue.isNotEmpty()) {
                    val message = retryQueue.removeFirst()
                    try {
                        messageStatusFlow.emit(message.message.id to MessageStatus.SENDING)

                        val event = PieSocketEvent("message").apply {
                            data = JSONObject().apply {
                                put("id", message.message.id)
                                put("event", "message")
                                put("data", message.message.content)
                                put("isFromUser", true)
                                put("botId", message.message.botId)
                                put("timestamp", message.message.timestamp)
                            }.toString()
                        }
                        mainChannel?.publish(event)
                        sentMessageIds.add(message.message.id)

                        messageStatusFlow.emit(message.message.id to MessageStatus.SENT)
                        Log.d(TAG, "Successfully retried message: ${message.message.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to retry message: ${e.message}")
                        messageStatusFlow.emit(message.message.id to MessageStatus.ERROR)

                        retryQueue.addLast(message)
                        messageStatusFlow.emit(message.message.id to MessageStatus.QUEUED)
                        delay(1000)
                    }
                }
            } finally {
                isRetrying = false
            }
        }
    }

    override fun observeMessageStatus(): Flow<Pair<String, MessageStatus>> = messageStatusFlow
}
