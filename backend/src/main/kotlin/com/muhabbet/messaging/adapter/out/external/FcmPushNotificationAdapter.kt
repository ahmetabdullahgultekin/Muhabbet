package com.muhabbet.messaging.adapter.out.external

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.Notification
import com.muhabbet.messaging.domain.model.PushNotification
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import com.muhabbet.shared.config.AsyncConfig
import com.muhabbet.shared.event.PushTokenRejectedEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.io.FileInputStream

@Component
@ConditionalOnProperty(name = ["muhabbet.fcm.enabled"], havingValue = "true")
class FcmPushNotificationAdapter internal constructor(
    private val credentialsPath: String,
    private val eventPublisher: ApplicationEventPublisher,
    /** The Firebase client, resolved per send. A seam so the error mapping can be tested offline. */
    private val messaging: () -> FirebaseMessaging
) : PushNotificationPort {

    @Autowired
    constructor(
        @Value("\${muhabbet.fcm.credentials-path}") credentialsPath: String,
        eventPublisher: ApplicationEventPublisher
    ) : this(credentialsPath, eventPublisher, { FirebaseMessaging.getInstance() })

    private val log = LoggerFactory.getLogger(javaClass)

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

    @Async(AsyncConfig.PUSH_EXECUTOR)
    override fun sendPush(pushToken: String, notification: PushNotification) {
        val message = Message.builder()
            .setToken(pushToken)
            .setNotification(
                Notification.builder()
                    .setTitle(notification.title)
                    .setBody(notification.body)
                    .build()
            )
            .putAllData(notification.data)
            .setAndroidConfig(
                AndroidConfig.builder()
                    // Replaces a push still queued for a device that is currently offline, so a
                    // phone coming back online gets one notification per conversation and not the
                    // whole backlog.
                    .setCollapseKey(notification.collapseKey)
                    .setNotification(
                        AndroidNotification.builder()
                            // Replaces one already showing in the tray. The queue-side collapse key
                            // does nothing for a device that was awake the whole time, which is the
                            // case #469 was actually reported against.
                            .setTag(notification.collapseKey)
                            .build()
                    )
                    .build()
            )
            .build()

        try {
            val messageId = messaging().send(message)
            log.debug("FCM sent: messageId={}, token={}...", messageId, pushToken.take(10))
        } catch (e: FirebaseMessagingException) {
            val code = e.messagingErrorCode
            log.warn("FCM send failed: token={}..., code={}, error={}", pushToken.take(10), code, e.message)
            if (isPermanentTokenRejection(code)) {
                // The auth module owns the device row; it forgets the token on this event (#908).
                eventPublisher.publishEvent(PushTokenRejectedEvent(pushToken, reason = code.toString()))
            }
        } catch (e: Exception) {
            log.warn("FCM send failed: token={}..., error={}", pushToken.take(10), e.message)
        }
    }

    companion object {
        /**
         * The FCM answers that mean *this token* will never work again, as opposed to this attempt.
         *
         * - `UNREGISTERED`: the app was uninstalled or the token expired.
         * - `SENDER_ID_MISMATCH`: the token belongs to another Firebase project.
         * - `INVALID_ARGUMENT`: in the HTTP v1 API this is also how a malformed token is reported.
         *   It can mean a malformed *payload* too, but the payload here is server-composed and
         *   bounded (`PushNotificationComposer.MAX_BODY_LENGTH`), so the token is the variable part.
         *
         * Everything else — quota, unavailable, internal, auth — is about the attempt or about us,
         * and dropping a working token for those would silently end that phone's notifications.
         * A null code (a transport failure with no FCM answer) is in that second group.
         */
        internal fun isPermanentTokenRejection(code: MessagingErrorCode?): Boolean =
            code == MessagingErrorCode.UNREGISTERED ||
                code == MessagingErrorCode.SENDER_ID_MISMATCH ||
                code == MessagingErrorCode.INVALID_ARGUMENT
    }
}
