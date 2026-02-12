package com.muhabbet.app.platform

interface PushTokenProvider {
    suspend fun getToken(): String?
}
