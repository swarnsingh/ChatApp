package com.swarn.chatapp.domain.model

import java.util.UUID

enum class MessageStatus {
    QUEUED,      // Message is queued for sending
    SENDING,     // Message is being sent
    SENT,        // Message has been sent to server
    DELIVERED,   // Message has been delivered to recipient
    READ,        // Message has been read by recipient
    UNREAD,     // Message has not been read
    ERROR        // Message failed to send
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val botId: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromUser: Boolean,
    val isRead: Boolean = false,
    val isQueued: Boolean = false,
    val isError: Boolean = false,
    val errorMessage: String? = null,
    val status: MessageStatus = MessageStatus.QUEUED
) 