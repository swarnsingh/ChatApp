package com.swarn.chatapp.data.repository

import android.util.Log
import app.cash.turbine.test
import com.swarn.chatapp.data.remote.MockNetworkStateMonitor
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.MessageStatus
import com.swarn.chatapp.domain.websocket.ConnectionState
import com.swarn.chatapp.domain.websocket.WebSocketClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ChatRepositoryImplTest {
    private lateinit var repository: ChatRepositoryImpl
    private lateinit var webSocketClient: WebSocketClient
    private lateinit var networkStateMonitor: MockNetworkStateMonitor
    private val testDispatcher = StandardTestDispatcher()
    private val connectionStateFlow = MutableStateFlow(ConnectionState.Disconnected)
    private val messageFlow = MutableSharedFlow<ChatMessage>()
    private val messageStatusFlow = MutableSharedFlow<Pair<String, MessageStatus>>()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        Dispatchers.setMain(testDispatcher)
        webSocketClient = mockk<WebSocketClient>(relaxed = true)
        every { webSocketClient.connectionStateFlow } returns connectionStateFlow
        every { webSocketClient.observeConnectionState() } returns connectionStateFlow
        every { webSocketClient.observeMessages() } returns messageFlow
        every { webSocketClient.observeMessageStatus() } returns messageStatusFlow
        every { webSocketClient.connect() } returns Unit
        every { webSocketClient.disconnect() } returns Unit
        coEvery { webSocketClient.sendMessage(any()) } returns Unit
        coEvery { webSocketClient.registerBot(any()) } returns flowOf(Unit)

        networkStateMonitor = MockNetworkStateMonitor()
        networkStateMonitor.setNetworkState(true)
        repository = ChatRepositoryImpl(webSocketClient, networkStateMonitor)
        testDispatcher.scheduler.advanceUntilIdle() // Process initial setup
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when sending message and online, should send through websocket`() = runTest {
        // Given
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            botId = "1",
            content = "Test message",
            timestamp = System.currentTimeMillis(),
            isFromUser = true,
            status = MessageStatus.QUEUED
        )
        networkStateMonitor.setNetworkState(true)
        val messageSlot = slot<ChatMessage>()
        coEvery { webSocketClient.sendMessage(capture(messageSlot)) } returns Unit

        // When
        repository.sendMessage(message)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { webSocketClient.sendMessage(any()) }
        assert(messageSlot.captured.content == message.content)
        assert(messageSlot.captured.botId == message.botId)
    }

    @Test
    fun `when sending message and offline, should queue message`() = runTest {
        // Given
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            botId = "1",
            content = "Test message",
            timestamp = System.currentTimeMillis(),
            isFromUser = true,
            status = MessageStatus.QUEUED
        )
        networkStateMonitor.setNetworkState(false)

        // When
        repository.sendMessage(message)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        repository.getMessagesForBot("1").test(timeout = 5.seconds) {
            val messages = awaitItem()
            assert(messages.isNotEmpty()) {
                "Expected messages to be non-empty"
            }
            assert(messages.first().status == MessageStatus.QUEUED) {
                "Expected message status to be QUEUED but was ${messages.first().status}"
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when marking message as read, should update message status`() = runTest {
        // Given
        val messageId = "test-message-id"
        val message = ChatMessage(
            id = messageId,
            botId = "1",
            content = "Test message",
            timestamp = System.currentTimeMillis(),
            isFromUser = false,
            status = MessageStatus.UNREAD
        )
        repository.sendMessage(message)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        repository.markMessageAsRead(messageId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        repository.getMessagesForBot("1").test(timeout = 5.seconds) {
            val messages = awaitItem()
            assert(messages.isNotEmpty()) {
                "Expected messages to be non-empty"
            }
            assert(messages.first().isRead) {
                "Expected message to be marked as read"
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when observing connection state, should emit websocket connection state`() = runTest {
        // Given
        connectionStateFlow.value = ConnectionState.Connected

        // When & Then
        repository.observeConnectionState().test(timeout = 5.seconds) {
            assert(awaitItem() == ConnectionState.Connected) {
                "Expected connection state to be Connected"
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when observing messages, should emit messages for specific bot`() = runTest {
        // Given
        val botId = "1"
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            botId = botId,
            content = "Test message",
            timestamp = System.currentTimeMillis(),
            isFromUser = true,
            status = MessageStatus.QUEUED
        )
        repository.sendMessage(message)
        testDispatcher.scheduler.advanceUntilIdle()

        // When & Then
        repository.getMessagesForBot(botId).test(timeout = 5.seconds) {
            val messages = awaitItem()
            assert(messages.isNotEmpty()) {
                "Expected messages to be non-empty"
            }
            assert(messages.first().botId == botId) {
                "Expected message botId to be $botId but was ${messages.first().botId}"
            }
            cancelAndIgnoreRemainingEvents()
        }
    }
} 