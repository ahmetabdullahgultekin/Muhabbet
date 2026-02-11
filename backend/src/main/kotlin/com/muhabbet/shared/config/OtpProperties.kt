package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "muhabbet.otp")
data class OtpProperties(
    val length: Int = 6,
    val expirySeconds: Int = 300,
    val maxAttempts: Int = 5,
    val cooldownSeconds: Int = 60,
    val mockEnabled: Boolean = false
)
