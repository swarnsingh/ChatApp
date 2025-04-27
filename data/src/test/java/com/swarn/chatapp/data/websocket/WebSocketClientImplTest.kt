package com.swarn.chatapp.data.websocket

import android.util.Log
import app.cash.turbine.test
import com.piesocket.channels.Channel
import com.piesocket.channels.PieSocket
import com.piesocket.channels.misc.PieSocketEvent
import com.piesocket.channels.misc.PieSocketEventListener
import com.swarn.chatapp.data.remote.ApiKeys
import com.swarn.chatapp.data.remote.NetworkStateMonitor
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.MessageStatus
import com.swarn.chatapp.domain.websocket.ConnectionState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class WebSocketClientImplTest {

    private lateinit var apiKeys: ApiKeys
    private lateinit var networkStateMonitor: NetworkStateMonitor
    private lateinit var webSocketClient: WebSocketClientImpl
    private lateinit var piesocket: PieSocket
    private lateinit var channel: Channel
    private val messageListenerSlot = slot<PieSocketEventListener>()
    private val connectionListenerSlot = slot<PieSocketEventListener>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock Log class
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        // Initialize mocks
        apiKeys = mockk(relaxed = true)
        networkStateMonitor = mockk(relaxed = true)
        piesocket = mockk(relaxed = true)
        channel = mockk(relaxed = true)

        // Mock PieSocket constructor
        mockkConstructor(PieSocket::class)
        every { anyConstructed<PieSocket>().join(any()) } returns channel
        every { channel.listen(eq("message"), capture(messageListenerSlot)) } returns Unit
        every {
            channel.listen(
                eq("connection_state"),
                capture(connectionListenerSlot)
            )
        } returns Unit
        every { channel.publish(any()) } returns Unit
        every { channel.disconnect() } returns Unit
        every { anyConstructed<PieSocket>().leave(any()) } returns Unit

        // Initialize WebSocketClient
        webSocketClient = WebSocketClientImpl(apiKeys, networkStateMonitor, testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        webSocketClient.disconnect()
    }

    @Test
    fun `connect succeeds when network is available`() = runTest {
        every { apiKeys.getPieSocketApiKey() } returns "fake_key"
        every { apiKeys.getPieSocketClusterId() } returns "fake_cluster"
        coEvery { networkStateMonitor.observe() } returns flowOf(true)

        webSocketClient.observeConnectionState().test {
            // Initial state should be Disconnected
            assertEquals(ConnectionState.Disconnected, awaitItem())

            webSocketClient.connect()
            testDispatcher.scheduler.advanceUntilIdle()

            // Should transition to Connecting
            assertEquals(ConnectionState.Connecting, awaitItem())

            // Simulate successful connection
            connectionListenerSlot.captured.handleEvent(PieSocketEvent("connection_state").apply {
                data = "connected"
            })
            testDispatcher.scheduler.advanceUntilIdle()

            // Should transition to Connected
            assertEquals(ConnectionState.Connected, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `connect fails when network is unavailable`() = runTest {
        coEvery { networkStateMonitor.observe() } returns flowOf(false)

        webSocketClient.observeConnectionState().test {
            // Initial state should be Disconnected
            assertEquals(ConnectionState.Disconnected, awaitItem())

            webSocketClient.connect()
            testDispatcher.scheduler.advanceUntilIdle()

            // Should transition to NetworkUnavailable
            assertEquals(ConnectionState.NetworkUnavailable, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `sendMessage queues message if not connected`() = runTest {
        val message = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = "Test message",
            botId = "test_bot",
            isFromUser = true,
            timestamp = System.currentTimeMillis(),
            status = MessageStatus.SENDING
        )

        webSocketClient.observeMessageStatus().test {
            webSocketClient.sendMessage(message)
            testDispatcher.scheduler.advanceUntilIdle()

            val (id, status) = awaitItem()
            assertEquals(message.id, id)
            assertEquals(MessageStatus.QUEUED, status)
            cancelAndConsumeRemainingEvents()
        }
    }
} 