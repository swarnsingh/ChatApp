package com.swarn.chatapp.data.di

import android.content.Context
import com.swarn.chatapp.data.remote.ApiKeys
import com.swarn.chatapp.data.remote.ApiKeysImpl
import com.swarn.chatapp.data.remote.NetworkStateMonitor
import com.swarn.chatapp.data.remote.NetworkStateMonitorImpl
import com.swarn.chatapp.data.repository.ChatRepositoryImpl
import com.swarn.chatapp.data.websocket.WebSocketClientImpl
import com.swarn.chatapp.domain.repository.ChatRepository
import com.swarn.chatapp.domain.websocket.WebSocketClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    companion object {
        @Provides
        @Singleton
        fun provideNetworkStateMonitor(
            @ApplicationContext context: Context,
        ): NetworkStateMonitor {
            return NetworkStateMonitorImpl(context)
        }

        @Provides
        @Singleton
        fun provideWebSocketClient(
            apiKeys: ApiKeys,
            networkStateMonitor: NetworkStateMonitor,
        ): WebSocketClient {
            return WebSocketClientImpl(apiKeys, networkStateMonitor)
        }

        @Provides
        @Singleton
        fun provideChatRepository(
            webSocketClient: WebSocketClient,
            networkStateMonitor: NetworkStateMonitor,
        ): ChatRepository {
            return ChatRepositoryImpl(webSocketClient, networkStateMonitor)
        }
    }

    @Binds
    @Singleton
    internal abstract fun bindApiKeys(impl: ApiKeysImpl): ApiKeys
} 