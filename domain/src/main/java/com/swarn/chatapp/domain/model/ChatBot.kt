package com.swarn.chatapp.domain.model

data class ChatBot(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val isUnread: Boolean = false
) 