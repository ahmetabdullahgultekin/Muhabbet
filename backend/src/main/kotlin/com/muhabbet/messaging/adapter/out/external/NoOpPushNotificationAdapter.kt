package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["muhabbet.fcm.enabled"], havingValue = "false", matchIfMissing = true)
class NoOpPushNotificationAdapter : PushNotificationPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendPush(pushToken: String, title: String, body: String, data: Map<String, String>) {
        log.debug("Push notification skipped (FCM disabled): title={}, token={}...", title, pushToken.take(10))
    }
}
