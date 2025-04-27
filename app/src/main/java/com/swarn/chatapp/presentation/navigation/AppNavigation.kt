package com.swarn.chatapp.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.swarn.chatapp.presentation.ui.ChatListScreen
import com.swarn.chatapp.presentation.ui.ChatScreen
import com.swarn.chatapp.presentation.viewmodel.ChatListViewModel
import com.swarn.chatapp.presentation.viewmodel.ChatViewModel

@Composable
fun AppNavigation(
    navController: NavHostController,
    chatListViewModel: ChatListViewModel = hiltViewModel()
) {
    val uiState by chatListViewModel.uiState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "chatList"
    ) {
        composable("chatList") {
            ChatListScreen(
                uiState = uiState,
                onBotSelected = { botId ->
                    uiState.conversationPreviews
                        .find { it.id == botId }
                        ?.let { preview ->
                            if (preview.isUnread) {
                                chatListViewModel.markMessageAsRead(preview.id)
                            }
                        }
                    navController.navigate("chat/$botId")
                },
                onSendMessage = { content ->
                    chatListViewModel.sendMessage(content)
                },
                onMarkMessageAsRead = { messageId ->
                    chatListViewModel.markMessageAsRead(messageId)
                },
                onCreateBot = { name ->
                    chatListViewModel.createBot(name)
                }
            )
        }

        composable("chat/{botId}") { backStackEntry ->
            val botId = backStackEntry.arguments?.getString("botId") ?: "bot1"
            val chatViewModel: ChatViewModel = hiltViewModel()
            
            ChatScreen(
                uiState = chatViewModel.uiState.collectAsState().value,
                botId = botId,
                onBackClick = {
                    navController.popBackStack()
                },
                onSendMessage = { content ->
                    chatViewModel.sendMessage(content)
                },
                onMarkMessageAsRead = { messageId ->
                    chatViewModel.markMessageAsRead(messageId)
                }
            )
        }
    }
} 