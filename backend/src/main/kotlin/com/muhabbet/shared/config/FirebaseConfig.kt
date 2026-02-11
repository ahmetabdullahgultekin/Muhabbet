package com.muhabbet.shared.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@Configuration
@ConditionalOnProperty(name = ["muhabbet.firebase.enabled"], havingValue = "true")
class FirebaseConfig(
    @Value("\${muhabbet.firebase.credentials-path:}") private val credentialsPath: String
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun init() {
        if (FirebaseApp.getApps().isNotEmpty()) {
            log.info("Firebase already initialized")
            return
        }

        val options = if (credentialsPath.isNotBlank()) {
            log.info("Initializing Firebase from credentials file: {}", credentialsPath)
            FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(FileInputStream(credentialsPath)))
                .build()
        } else {
            log.info("Initializing Firebase with Application Default Credentials")
            FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .build()
        }

        FirebaseApp.initializeApp(options)
        log.info("Firebase initialized successfully")
    }
}
