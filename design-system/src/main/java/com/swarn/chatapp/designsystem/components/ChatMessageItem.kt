package com.swarn.chatapp.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swarn.chatapp.designsystem.R
import com.swarn.chatapp.domain.model.ChatMessage
import com.swarn.chatapp.domain.model.MessageStatus
import com.swarn.chatapp.domain.util.DateTimeUtils
import java.util.UUID

@Composable
fun ChatMessageItem(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val maxMessageWidth = (screenWidth * 0.75f) // 75% of screen width

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isFromUser) 16.dp else 4.dp,
                        bottomEnd = if (message.isFromUser) 4.dp else 16.dp
                    )
                )
                .background(
                    when (message.status) {
                        MessageStatus.QUEUED -> MaterialTheme.colorScheme.tertiaryContainer
                        MessageStatus.SENDING -> MaterialTheme.colorScheme.tertiaryContainer
                        MessageStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                        else -> if (message.isFromUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .padding(12.dp)
                .widthIn(max = maxMessageWidth)
        ) {
            Column {
                Text(
                    text = message.content,
                    color = if (message.isFromUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (message.isFromUser) {
                        when (message.status) {
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
                            else -> {
                                MessageStatusIcon(
                                    status = message.status,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = DateTimeUtils.formatMessageTime(message.timestamp),
                        color = when (message.status) {
                            MessageStatus.ERROR -> MaterialTheme.colorScheme.error
                            else -> if (message.isFromUser) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Preview(name = "User Message - Sent")
@Composable
private fun PreviewUserMessageSent() {
    ChatMessageItem(
        message = ChatMessage(
            id = UUID.randomUUID().toString(),
            botId = "1",
            content = "Hello, how are you?",
            timestamp = System.currentTimeMillis(),
            isFromUser = true,
            status = MessageStatus.SENT
        )
    )
}

@Preview(name = "User Message - Queued")
@Composable
private fun PreviewUserMessageQueued() {
    ChatMessageItem(
        message = ChatMessage(
            id = UUID.randomUUID().toString(),
            botId = "1",
            content = "This message is queued",
            timestamp = System.currentTimeMillis(),
            isFromUser = true,
            status = MessageStatus.QUEUED
        )
    )
}

@Preview(name = "User Message - Sending")
@Composable
private fun PreviewUserMessageSending() {
    ChatMessageItem(
        message = ChatMessage(
            id = UUID.randomUUID().toString(),
            botId = "1",
            content = "This message is sending...",
            timestamp = System.currentTimeMillis(),
            isFromUser = true,
            status = MessageStatus.SENDING
        )
    )
}

@Preview(name = "User Message - Error")
@Composable
private fun PreviewUserMessageError() {
    ChatMessageItem(
        message = ChatMessage(
            id = UUID.randomUUID().toString(),
            botId = "1",
            content = "This message failed to send",
            timestamp = System.currentTimeMillis(),
            isFromUser = true,
            status = MessageStatus.ERROR,
            errorMessage = "Network error"
        )
    )
}

@Preview(name = "Bot Message", showSystemUi = true)
@Composable
private fun PreviewBotMessage() {
    ChatMessageItem(
        message = ChatMessage(
            id = UUID.randomUUID().toString(),
            botId = "1",
            content = "I'm doing well, thank you!",
            timestamp = System.currentTimeMillis(),
            isFromUser = false,
            status = MessageStatus.DELIVERED
        )
    )
} 