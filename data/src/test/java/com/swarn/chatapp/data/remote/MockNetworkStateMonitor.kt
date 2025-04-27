package com.swarn.chatapp.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class MockNetworkStateMonitor : NetworkStateMonitor {
    private val _isOnline = MutableStateFlow(true)

    fun setNetworkState(isOnline: Boolean) {
        _isOnline.value = isOnline
    }

    override fun observe(): Flow<Boolean> = _isOnline
} 