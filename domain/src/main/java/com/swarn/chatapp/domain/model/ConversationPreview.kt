package com.swarn.chatapp.domain.model

data class ConversationPreview(
    val id: String,
    val botName: String,
    val lastMessage: String,
    val lastMessageId: String,
    val lastMessageIsFromUser: Boolean,
    val timestamp: Long,
    val isUnread: Boolean,
    val bot: ChatBot,
    val messageStatus: MessageStatus = MessageStatus.QUEUED
) 