package com.swarn.chatapp.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swarn.chatapp.R
import com.swarn.chatapp.designsystem.components.ChatMessageItem
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.MessageStatus
import com.swarn.chatapp.presentation.viewmodel.ChatUiState
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    botId: String,
    onBackClick: () -> Unit,
    onSendMessage: (String) -> Unit,
    onMarkMessageAsRead: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.messages) {
        uiState.messages.forEach { message ->
            if (!message.isFromUser && !message.isRead) {
                coroutineScope.launch {
                    onMarkMessageAsRead(message.id)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat with Bot $botId") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!uiState.isOnline) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = stringResource(R.string.no_internet_connection),
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (uiState.error != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = uiState.error,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            if (uiState.messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_messages),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    reverseLayout = true
                ) {
                    items(uiState.messages.reversed()) { message ->
                        ChatMessageItem(message = message)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message") }
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send"
                    )
                }
            }
        }
    }
}

@Preview(name = "Chat Screen - Empty")
@Composable
private fun PreviewChatScreenEmpty() {
    ChatScreen(
        uiState = ChatUiState(
            messages = emptyList(),
            isOnline = true,
            error = null
        ),
        botId = "1",
        onBackClick = {},
        onSendMessage = {},
        onMarkMessageAsRead = {}
    )
}

@Preview(name = "Chat Screen - With Messages")
@Composable
private fun PreviewChatScreenWithMessages() {
    ChatScreen(
        uiState = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    botId = "1",
                    content = "Hello!",
                    timestamp = System.currentTimeMillis(),
                    isFromUser = true,
                    status = MessageStatus.SENT
                ),
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    botId = "1",
                    content = "Hi there! How can I help you?",
                    timestamp = System.currentTimeMillis(),
                    isFromUser = false,
                    status = MessageStatus.DELIVERED
                )
            ),
            isOnline = true,
            error = null
        ),
        botId = "1",
        onBackClick = {},
        onSendMessage = {},
        onMarkMessageAsRead = {}
    )
}

@Preview(name = "Chat Screen - Offline")
@Composable
private fun PreviewChatScreenOffline() {
    ChatScreen(
        uiState = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    botId = "1",
                    content = "This message is queued",
                    timestamp = System.currentTimeMillis(),
                    isFromUser = true,
                    status = MessageStatus.QUEUED
                )
            ),
            isOnline = false,
            error = null
        ),
        botId = "1",
        onBackClick = {},
        onSendMessage = {},
        onMarkMessageAsRead = {}
    )
} 