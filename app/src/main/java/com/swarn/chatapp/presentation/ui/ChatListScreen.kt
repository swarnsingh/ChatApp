package com.swarn.chatapp.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swarn.chatapp.R
import com.swarn.chatapp.designsystem.components.MessageStatusIcon
import com.swarn.chatapp.domain.model.ChatBot
import com.swarn.chatapp.domain.model.ConversationPreview
import com.swarn.chatapp.domain.model.MessageStatus
import com.swarn.chatapp.domain.util.DateTimeUtils
import com.swarn.chatapp.presentation.viewmodel.ChatListUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    uiState: ChatListUiState,
    onBotSelected: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onMarkMessageAsRead: (String) -> Unit,
    onCreateBot: (String) -> Unit
) {
    var showCreateBotDialog by remember { mutableStateOf(false) }
    var newBotName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chats") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateBotDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create new bot"
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.conversationPreviews.isEmpty()) {
                EmptyState(
                    isOnline = uiState.isOnline,
                    onCreateBot = { showCreateBotDialog = true }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(uiState.conversationPreviews) { preview ->
                        ConversationItem(
                            preview = preview,
                            onClick = { onBotSelected(preview.id) }
                        )
                    }
                }
            }

            if (showCreateBotDialog) {
                CreateBotDialog(
                    botName = newBotName,
                    onBotNameChange = { newBotName = it },
                    onDismiss = { showCreateBotDialog = false },
                    onConfirm = {
                        if (newBotName.isNotBlank()) {
                            onCreateBot(newBotName)
                            newBotName = ""
                        }
                        showCreateBotDialog = false
                    }
                )
            }
        }
    }
}

@Preview(name = "Chat List Screen - Empty")
@Composable
private fun PreviewChatListScreenEmpty() {
    ChatListScreen(
        uiState = ChatListUiState(
            conversationPreviews = emptyList(),
            isOnline = true
        ),
        onBotSelected = {},
        onSendMessage = {},
        onMarkMessageAsRead = {},
        onCreateBot = {}
    )
}

@Preview(name = "Chat List Screen - With Conversations")
@Composable
private fun PreviewChatListScreenWithConversations() {
    ChatListScreen(
        uiState = ChatListUiState(
            conversationPreviews = listOf(
                ConversationPreview(
                    id = "1",
                    botName = "Assistant",
                    lastMessage = "Hello! How can I help you?",
                    lastMessageId = "msg1",
                    lastMessageIsFromUser = false,
                    timestamp = System.currentTimeMillis(),
                    isUnread = true,
                    bot = ChatBot(id = "1", name = "Assistant")
                ),
                ConversationPreview(
                    id = "2",
                    botName = "Support",
                    lastMessage = "Your issue has been resolved",
                    lastMessageId = "msg2",
                    lastMessageIsFromUser = false,
                    timestamp = System.currentTimeMillis() - 3600000,
                    isUnread = false,
                    bot = ChatBot(id = "2", name = "Support")
                )
            ),
            isOnline = true
        ),
        onBotSelected = {},
        onSendMessage = {},
        onMarkMessageAsRead = {},
        onCreateBot = {}
    )
}

@Preview(name = "Chat List Screen - Offline")
@Composable
private fun PreviewChatListScreenOffline() {
    ChatListScreen(
        uiState = ChatListUiState(
            conversationPreviews = emptyList(),
            isOnline = false
        ),
        onBotSelected = {},
        onSendMessage = {},
        onMarkMessageAsRead = {},
        onCreateBot = {}
    )
}

@Composable
private fun EmptyState(
    isOnline: Boolean,
    onCreateBot: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isOnline) {
            Text(
                text = stringResource(R.string.no_internet_connection),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = stringResource(R.string.no_conversations),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(name = "Empty State - Online")
@Composable
private fun PreviewEmptyStateOnline() {
    EmptyState(
        isOnline = true,
        onCreateBot = {}
    )
}

@Preview(name = "Empty State - Offline")
@Composable
private fun PreviewEmptyStateOffline() {
    EmptyState(
        isOnline = false,
        onCreateBot = {}
    )
}

@Composable
private fun ConversationItem(
    preview: ConversationPreview,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preview.botName.first().toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = preview.botName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (preview.lastMessageIsFromUser) {
                        when (preview.messageStatus) {
                            MessageStatus.QUEUED -> {
                                MessageStatusIcon(
                                    status = MessageStatus.QUEUED,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            MessageStatus.SENT -> {
                                MessageStatusIcon(
                                    status = MessageStatus.READ,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            else -> {}
                        }
                    }
                    Text(
                        text = preview.lastMessage,
                        style = when (preview.messageStatus) {
                            MessageStatus.QUEUED -> MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                            )
                            MessageStatus.SENDING -> MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f)
                            )
                            MessageStatus.SENT -> MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            MessageStatus.DELIVERED -> MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            MessageStatus.READ -> MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            MessageStatus.UNREAD -> MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.primary
                            )
                            MessageStatus.ERROR -> MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = DateTimeUtils.formatConversationTime(preview.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (preview.isUnread) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

@Preview(name = "Conversation Item - Unread")
@Composable
private fun PreviewConversationItemUnread() {
    ConversationItem(
        preview = ConversationPreview(
            id = "1",
            botName = "Assistant",
            lastMessage = "Hello! How can I help you?",
            lastMessageId = "msg1",
            lastMessageIsFromUser = false,
            timestamp = System.currentTimeMillis(),
            isUnread = true,
            bot = ChatBot(id = "1", name = "Assistant")
        ),
        onClick = {}
    )
}

@Preview(name = "Conversation Item - Read")
@Composable
private fun PreviewConversationItemRead() {
    ConversationItem(
        preview = ConversationPreview(
            id = "1",
            botName = "Assistant",
            lastMessage = "Hello! How can I help you?",
            lastMessageId = "msg1",
            lastMessageIsFromUser = false,
            timestamp = System.currentTimeMillis(),
            isUnread = false,
            bot = ChatBot(id = "1", name = "Assistant")
        ),
        onClick = {}
    )
}

@Composable
private fun CreateBotDialog(
    botName: String,
    onBotNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_bot)) },
        text = {
            OutlinedTextField(
                value = botName,
                onValueChange = onBotNameChange,
                label = { Text(stringResource(R.string.bot_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = botName.isNotBlank()
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Preview(name = "Create Bot Dialog")
@Composable
private fun PreviewCreateBotDialog() {
    CreateBotDialog(
        botName = "New Bot",
        onBotNameChange = {},
        onDismiss = {},
        onConfirm = {}
    )
} 