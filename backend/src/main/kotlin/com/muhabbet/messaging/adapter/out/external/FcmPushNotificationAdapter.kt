package com.muhabbet.messaging.adapter.out.external

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
            val messageId = FirebaseMessaging.getInstance().send(message)
            log.debug("FCM sent: messageId={}, token={}...", messageId, pushToken.take(10))
        } catch (e: Exception) {
            log.warn("FCM send failed: token={}..., error={}", pushToken.take(10), e.message)
        }
    }
}
