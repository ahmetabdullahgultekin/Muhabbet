package com.muhabbet.app.platform

class IosPushTokenProvider : PushTokenProvider {
    override suspend fun getToken(): String? = null
}
