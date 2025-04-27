package com.swarn.chatapp.data.remote

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeysImpl @Inject constructor() : ApiKeys {
    external override fun getPieSocketApiKey(): String
    external override fun getPieSocketClusterId(): String

    companion object {
        init {
            System.loadLibrary("chatapp")
        }
    }
} 