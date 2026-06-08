package com.muhabbet.messaging.adapter.out.external

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import com.muhabbet.messaging.domain.port.out.PushTokenInvalidationPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.io.FileInputStream

@Component
@ConditionalOnProperty(name = ["muhabbet.fcm.enabled"], havingValue = "true")
open class FcmPushNotificationAdapter(
    @Value("\${muhabbet.fcm.credentials-path}") private val credentialsPath: String,
    private val pushTokenInvalidationPort: PushTokenInvalidationPort
) : PushNotificationPort {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * Terminal FCM error codes that mean the token is permanently invalid and must be
         * removed (the app was uninstalled / token rotated, or the token is malformed /
         * belongs to a different sender). Anything else (UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED,
         * network) is transient — the token is still valid, keep it and let sync/retry handle it.
         */
        private val DEAD_TOKEN_CODES = setOf(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.INVALID_ARGUMENT,
            MessagingErrorCode.SENDER_ID_MISMATCH
        )
    }

    @PostConstruct
    fun init() {
        if (FirebaseApp.getApps().isEmpty()) {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(FileInputStream(credentialsPath)))
                .build()
            FirebaseApp.initializeApp(options)
            log.info("Firebase initialized from {}", credentialsPath)
        }
    }

    override fun sendPush(pushToken: String, title: String, body: String, data: Map<String, String>) {
        val message = Message.builder()
            .setToken(pushToken)
            .setNotification(
                Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build()
            )
            .putAllData(data)
            .build()

        try {
            val messageId = sendToFirebase(message)
            log.debug("FCM sent: messageId={}, token={}...", messageId, pushToken.take(10))
        } catch (e: Exception) {
            val deadTokenCode = (e as? FirebaseMessagingException)?.messagingErrorCode
                ?.takeIf { it in DEAD_TOKEN_CODES }
            if (deadTokenCode != null) {
                // Terminal: the token can never succeed — remove it so it is never retried.
                log.info(
                    "FCM token dead ({}) — invalidating: token={}...",
                    deadTokenCode, pushToken.take(10)
                )
                runCatching { pushTokenInvalidationPort.invalidate(pushToken) }
                    .onFailure { log.warn("Push token invalidation failed: token={}..., error={}", pushToken.take(10), it.message) }
            } else {
                // Transient (network/quota/unavailable): token still valid, keep it.
                log.warn("FCM send failed: token={}..., error={}", pushToken.take(10), e.message)
            }
        }
    }

    /**
     * Seam around the live Firebase call so the error-handling path is unit-testable
     * without credentials (a test overrides this to throw a [FirebaseMessagingException]).
     */
    protected open fun sendToFirebase(message: Message): String =
        FirebaseMessaging.getInstance().send(message)
}
