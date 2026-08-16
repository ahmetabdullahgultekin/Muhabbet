package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.PushNotification

interface PushNotificationPort {
    /**
     * Takes a composed [PushNotification] rather than loose title/body/data strings so that the
     * collapse key travels with the text it belongs to. When they were separate arguments the
     * caller decided the wording, which is how a constant title survived in one broadcaster while
     * the other had already grown per-type bodies (#469).
     */
    fun sendPush(pushToken: String, notification: PushNotification)
}
