package com.swarn.chatapp.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.MessageStatus
import com.swarn.chatapp.domain.repository.ChatRepository
import com.swarn.chatapp.domain.websocket.ConnectionState
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private lateinit var viewModel: ChatViewModel
    private lateinit var repository: ChatRepository
    private val testDispatcher = StandardTestDispatcher()
    private val savedStateHandle = SavedStateHandle().apply {
        set("botId", "1")
    }
    private val messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val networkStateFlow = MutableStateFlow(true)
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
        repository = mockk(relaxed = true) {
            every { getMessagesForBot(any()) } returns messagesFlow
            every { observeNetworkState() } returns networkStateFlow
            every { observeConnectionState() } returns connectionStateFlow
            every { observeMessages() } returns messageFlow
            every { observeMessageStatus() } returns messageStatusFlow
            coEvery { sendMessage(any()) } returns Unit
            coEvery { markMessageAsRead(any()) } returns Unit
        }
        viewModel = ChatViewModel(repository, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle() // Process initial setup
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when sending message, should update UI state with new message`() = runTest {
        // Given
        val messageContent = "Test message"
        val messageSlot = slot<ChatMessage>()
        coEvery { repository.sendMessage(capture(messageSlot)) } returns Unit

        // When
        viewModel.sendMessage(messageContent)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assert(messageSlot.captured.content == messageContent)
        assert(messageSlot.captured.isFromUser)
        assert(messageSlot.captured.botId == "1")
    }

    @Test
    fun `when marking message as read, should call repository`() = runTest {
        // Given
        val messageId = "test-message-id"
        coEvery { repository.markMessageAsRead(messageId) } returns Unit

        // When
        viewModel.markMessageAsRead(messageId)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        coVerify { repository.markMessageAsRead(messageId) }
    }

    @Test
    fun `when connection state changes, should update UI state`() = runTest {
        // Given
        connectionStateFlow.value = ConnectionState.Connected
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        viewModel.uiState.test {
            val state = awaitItem()
            assert(state.connectionState == ConnectionState.Connected)
            cancelAndIgnoreRemainingEvents()
        }
    }
}