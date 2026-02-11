package com.muhabbet.shared.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "muhabbet.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpiry: Long = 900,
    val refreshTokenExpiry: Long = 2592000,
    val issuer: String = "muhabbet"
)
