package com.swarn.chatapp.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.swarn.chatapp.domain.model.MessageStatus

@Composable
fun MessageStatusIcon(
    status: MessageStatus,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = when (status) {
        MessageStatus.QUEUED -> Pair(Icons.Outlined.AccessTime, MaterialTheme.colorScheme.tertiary)
        MessageStatus.SENDING -> Pair(Icons.Outlined.Sync, MaterialTheme.colorScheme.tertiary)
        MessageStatus.SENT -> Pair(Icons.Outlined.Check, MaterialTheme.colorScheme.primary)
        MessageStatus.DELIVERED -> Pair(Icons.Outlined.Check, MaterialTheme.colorScheme.primary)
        MessageStatus.READ -> Pair(Icons.Outlined.DoneAll, MaterialTheme.colorScheme.primary)
        MessageStatus.UNREAD -> Pair(Icons.Outlined.Check, MaterialTheme.colorScheme.primary)
        MessageStatus.ERROR -> Pair(Icons.Outlined.Error, MaterialTheme.colorScheme.error)
    }

    Icon(
        imageVector = icon,
        contentDescription = "Message status: $status",
        modifier = modifier,
        tint = tint
    )
} 