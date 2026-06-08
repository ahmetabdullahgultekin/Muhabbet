package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Inert push adapter active ONLY when push is genuinely disabled
 * (`muhabbet.fcm.enabled=false` or unset). It exists so the rest of the app always has a
 * [PushNotificationPort] bean.
 *
 * Selection is mutually exclusive with [FcmPushNotificationAdapter] (`havingValue="true"`):
 * the two never coexist, and if `muhabbet.fcm.enabled` is set to a non-boolean value neither
 * loads and the context fails fast rather than silently shadowing a misconfigured FCM. A
 * startup WARN makes "push is doing nothing" loud so it is never mistaken for working push.
 */
@Component
@ConditionalOnProperty(name = ["muhabbet.fcm.enabled"], havingValue = "false", matchIfMissing = true)
class NoOpPushNotificationAdapter : PushNotificationPort {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun warnPushDisabled() {
        log.warn(
            "PUSH NOTIFICATIONS DISABLED — running NoOp push adapter (muhabbet.fcm.enabled is false/unset). " +
                "Offline users will NOT receive push notifications. Set FCM_ENABLED=true + FCM_CREDENTIALS_PATH for production."
        )
    }

    override fun sendPush(pushToken: String, title: String, body: String, data: Map<String, String>) {
        log.debug("Push notification skipped (FCM disabled): title={}, token={}...", title, pushToken.take(10))
    }
}
