package com.muhabbet.shared.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

data class JwtClaims(
    val userId: UUID,
    val deviceId: UUID
)

@Component
class JwtProvider(private val jwtProperties: JwtProperties) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    private val signingKey: SecretKey = Keys.hmacShaKeyFor(
        jwtProperties.secret.toByteArray(Charsets.UTF_8)
    )

    val accessTokenExpirySeconds: Long = jwtProperties.accessTokenExpiry

    fun generateAccessToken(userId: UUID, deviceId: UUID): String {
        val now = Date()
        val expiry = Date(now.time + jwtProperties.accessTokenExpiry * 1000)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("deviceId", deviceId.toString())
            .issuedAt(now)
            .expiration(expiry)
            .issuer(jwtProperties.issuer)
            .signWith(signingKey)
            .compact()
    }

    fun generateRefreshToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun validateToken(token: String): JwtClaims? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(jwtProperties.issuer)
                .build()
                .parseSignedClaims(token)
                .payload

            JwtClaims(
                userId = UUID.fromString(claims.subject),
                deviceId = UUID.fromString(claims["deviceId"] as String)
            )
        } catch (ex: Exception) {
            log.debug("JWT validation failed: {}", ex.message)
            null
        }
    }
}
