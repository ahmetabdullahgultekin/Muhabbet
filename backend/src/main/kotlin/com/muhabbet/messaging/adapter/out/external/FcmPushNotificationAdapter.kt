package com.muhabbet.messaging.adapter.out.external

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.AndroidNotification
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.muhabbet.messaging.domain.model.PushNotification
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import com.muhabbet.shared.config.AsyncConfig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import jakarta.annotation.PostConstruct
import java.io.FileInputStream

@Component
@ConditionalOnProperty(name = ["muhabbet.fcm.enabled"], havingValue = "true")
class FcmPushNotificationAdapter(
    @Value("\${muhabbet.fcm.credentials-path}") private val credentialsPath: String
) : PushNotificationPort {

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
            val messageId = FirebaseMessaging.getInstance().send(message)
            log.debug("FCM sent: messageId={}, token={}...", messageId, pushToken.take(10))
        } catch (e: Exception) {
            log.warn("FCM send failed: token={}..., error={}", pushToken.take(10), e.message)
        }
    }
}
