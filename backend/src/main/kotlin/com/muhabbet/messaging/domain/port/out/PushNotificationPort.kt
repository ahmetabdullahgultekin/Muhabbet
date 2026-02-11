package com.muhabbet.messaging.domain.port.out

interface PushNotificationPort {
    fun sendPush(pushToken: String, title: String, body: String, data: Map<String, String>)
}
