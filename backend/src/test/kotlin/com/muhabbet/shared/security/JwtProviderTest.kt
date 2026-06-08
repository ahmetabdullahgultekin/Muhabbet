package com.muhabbet.shared.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import java.util.Date
import java.util.UUID

/**
 * V&V — JWT token-validation completeness (signature / issuer / expiry / alg-confusion / claims).
 *
 * The existing JwtProviderSecretGuardTest only covers the boot-time secret guard. This suite pins the
 * runtime contract of [JwtProvider.validateToken]: a token must be rejected unless it is (a) signed
 * with the configured HS256 secret, (b) carries the expected issuer, and (c) is not expired — and a
 * privilege-bearing `admin` claim must never be self-grantable through the normal mint path.
 */
class JwtProviderTest {

    private val secret = "a-properly-long-production-secret-with-enough-entropy-1234567890"
    private val issuer = "muhabbet"

    private fun provider(
        secret: String = this.secret,
        accessTokenExpiry: Long = 900,
        issuer: String = this.issuer
    ) = JwtProvider(
        JwtProperties(
            secret = secret,
            accessTokenExpiry = accessTokenExpiry,
            refreshTokenExpiry = 2592000,
            issuer = issuer
        ),
        MockEnvironment()
    )

    @Test
    fun `validateToken should accept a token minted by the same provider`() {
        val p = provider()
        val userId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()

        val claims = p.validateToken(p.generateAccessToken(userId, deviceId))

        assertNotNull(claims)
        assertEquals(userId, claims!!.userId)
        assertEquals(deviceId, claims.deviceId)
        assertFalse(claims.isAdmin)
    }

    @Test
    fun `validateToken should reject a token signed with a different secret`() {
        val token = provider(secret = "different-but-still-long-enough-secret-abcdefghijklmnop")
            .generateAccessToken(UUID.randomUUID(), UUID.randomUUID())

        // Verified against the real provider's secret → signature mismatch → null.
        assertNull(provider().validateToken(token))
    }

    @Test
    fun `validateToken should reject a token with a tampered signature`() {
        val p = provider()
        val token = p.generateAccessToken(UUID.randomUUID(), UUID.randomUUID())
        // Replace the whole signature segment with a structurally-valid-but-wrong base64url value.
        // (Flipping a single char can collide with the same MAC bytes; replacing the segment cannot.)
        val (header, payload, _) = token.split(".")
        val tampered = "$header.$payload.AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        assertNull(p.validateToken(tampered))
    }

    @Test
    fun `validateToken should reject a token with a tampered payload`() {
        val p = provider()
        val token = p.generateAccessToken(UUID.randomUUID(), UUID.randomUUID())
        val parts = token.split(".")
        // Swap the payload for a different (valid base64url) claims set; the original signature no
        // longer covers it → verification must fail.
        val forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"sub":"${UUID.randomUUID()}","deviceId":"${UUID.randomUUID()}","iss":"$issuer"}"""
                .toByteArray()
        )
        val tampered = "${parts[0]}.$forgedPayload.${parts[2]}"

        assertNull(p.validateToken(tampered))
    }

    @Test
    fun `validateToken should reject a token from a different issuer`() {
        // Token minted with issuer "evil"; the validating provider requires "muhabbet".
        val token = provider(issuer = "evil").generateAccessToken(UUID.randomUUID(), UUID.randomUUID())

        assertNull(provider().validateToken(token))
    }

    @Test
    fun `validateToken should reject an expired token`() {
        // 0s expiry → expiration == issuedAt; parse must reject as expired.
        val p = provider(accessTokenExpiry = 0)
        val token = p.generateAccessToken(UUID.randomUUID(), UUID.randomUUID())
        Thread.sleep(10)

        assertNull(p.validateToken(token))
    }

    @Test
    fun `validateToken should reject a malformed token`() {
        val p = provider()
        assertNull(p.validateToken("not-a-jwt"))
        assertNull(p.validateToken(""))
        assertNull(p.validateToken("a.b.c"))
    }

    @Test
    fun `validateToken should reject an unsigned alg none token (alg-confusion)`() {
        // Forge an unsigned JWT (alg=none equivalent — no signature segment). verifyWith(SecretKey)
        // requires a MAC signature, so an unsigned token must NOT be accepted.
        val unsigned = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .claim("deviceId", UUID.randomUUID().toString())
            .issuer(issuer)
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .compact() // no signWith → unsecured/none

        assertNull(provider().validateToken(unsigned))
    }

    @Test
    fun `validateToken should not honor a self-asserted admin claim signed with a foreign key`() {
        // An attacker who does not hold the server secret cannot mint an admin token: even with
        // admin=true in the payload, a foreign-key signature fails verification → null.
        val foreignKey = Keys.hmacShaKeyFor(
            "attacker-controlled-secret-key-that-is-long-enough-1234567890".toByteArray()
        )
        val forged = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .claim("deviceId", UUID.randomUUID().toString())
            .claim("admin", true)
            .issuer(issuer)
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(foreignKey)
            .compact()

        assertNull(provider().validateToken(forged))
    }

    @Test
    fun `generateAccessToken should never set the admin claim`() {
        // Defense-in-depth assertion of the documented fail-closed posture: the normal mint path
        // produces only non-admin tokens, so admin-gated endpoints are unreachable without an
        // out-of-band admin mint (which does not exist) or the server secret.
        val p = provider()
        val claims = p.validateToken(p.generateAccessToken(UUID.randomUUID(), UUID.randomUUID()))

        assertNotNull(claims)
        assertFalse(claims!!.isAdmin)
    }
}
