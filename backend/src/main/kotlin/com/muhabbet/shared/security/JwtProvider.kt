package com.muhabbet.shared.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

data class JwtClaims(
    val userId: UUID,
    val deviceId: UUID,
    val isAdmin: Boolean = false
)

@Component
class JwtProvider(
    private val jwtProperties: JwtProperties,
    private val environment: Environment
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    // Lazy so the startup guard (validateSecret) runs and reports a clear message BEFORE JJWT's own
    // WeakKeyException would fire for a short secret. Built on first sign/verify, after boot validation.
    private val signingKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray(Charsets.UTF_8))
    }

    companion object {
        // The literal default declared in application.yml. If the live secret equals this, JWT_SECRET
        // was never overridden — fail closed so a forgeable, world-known signing key never boots.
        private const val DEV_DEFAULT_SECRET =
            "dev-secret-change-in-production-min-256-bits-long-key-here"

        // HS256 needs ≥256 bits of key material. Anything shorter is brute-forceable.
        private const val MIN_SECRET_BYTES = 32
    }

    /**
     * Fail-closed startup guard for the JWT signing secret. Runs on every profile.
     * Boot is aborted (IllegalStateException) when:
     *  - the secret is still the world-known dev default (in application.yml), or
     *  - the secret has fewer than 32 bytes of entropy (too weak for HS256).
     * This is the single guard that prevents shipping a forgeable token signer to production.
     */
    @PostConstruct
    fun validateSecret() {
        val secret = jwtProperties.secret
        val activeProfiles = environment.activeProfiles.toList()
        val profileLabel = if (activeProfiles.isEmpty()) "default" else activeProfiles.joinToString(",")

        check(secret != DEV_DEFAULT_SECRET) {
            "JWT_SECRET is still the dev default — refusing to start (profile=$profileLabel). " +
                "Set JWT_SECRET to a unique value with at least $MIN_SECRET_BYTES bytes of entropy."
        }
        check(secret.toByteArray(Charsets.UTF_8).size >= MIN_SECRET_BYTES) {
            "JWT_SECRET is too short for HS256 — refusing to start (profile=$profileLabel). " +
                "It must be at least $MIN_SECRET_BYTES bytes."
        }
        log.info("JWT signing secret validated (profile={}).", profileLabel)
    }

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
                deviceId = UUID.fromString(claims["deviceId"] as String),
                isAdmin = claims["admin"] as? Boolean ?: false
            )
        } catch (ex: Exception) {
            log.debug("JWT validation failed: {}", ex.message)
            null
        }
    }
}
