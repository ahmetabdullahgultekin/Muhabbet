package com.muhabbet.shared.security

import com.muhabbet.shared.AuthTestFixtures
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment
import java.util.Date
import java.util.UUID

/**
 * Signing-key rotation by `kid`, and the claims every access token must carry (#923).
 *
 * Before this, the provider signed and verified with one key and no key id, so rotating
 * `JWT_SECRET` after a leak logged every user out at once — which in practice meant it would not be
 * rotated. These pin the overlap window that makes rotation routine.
 */
class JwtKeyRingTest {

    private val oldSecret = "old-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256"
    private val newSecret = "new-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256"
    private val userId = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()

    private fun provider(
        secret: String,
        keyId: String,
        previous: List<JwtVerificationKey> = emptyList()
    ) = JwtProvider(
        AuthTestFixtures.jwtProperties().copy(secret = secret, keyId = keyId, previousKeys = previous),
        MockEnvironment()
    ).also { it.validateSecret() }

    @Test
    fun `should still validate a token signed with the previous key during the overlap window`() {
        val beforeRotation = provider(oldSecret, keyId = "k1").generateAccessToken(userId, deviceId, 0)
        val afterRotation = provider(newSecret, keyId = "k2", previous = listOf(JwtVerificationKey("k1", oldSecret)))

        val claims = afterRotation.validateToken(beforeRotation)

        assertEquals(userId, claims?.userId)
    }

    @Test
    fun `should reject a token from a retired key once it is dropped from the ring`() {
        val beforeRotation = provider(oldSecret, keyId = "k1").generateAccessToken(userId, deviceId, 0)
        val overlapOver = provider(newSecret, keyId = "k2")

        assertNull(overlapOver.validateToken(beforeRotation))
    }

    @Test
    fun `should sign only with the current key`() {
        val rotated = provider(newSecret, keyId = "k2", previous = listOf(JwtVerificationKey("k1", oldSecret)))
        val token = rotated.generateAccessToken(userId, deviceId, 0)

        // A ring that knows only the old key cannot verify it: the retired key never signs.
        assertNull(provider(oldSecret, keyId = "k1").validateToken(token))
    }

    @Test
    fun `should reject a token with an unknown kid even when the signature would match`() {
        // Signed with the server's real secret but labelled with a kid the ring does not hold:
        // the key is chosen by kid, never by trying every key.
        val token = Jwts.builder()
            .header().keyId("k-unknown").and()
            .subject(userId.toString())
            .audience().add("muhabbet-app").and()
            .claim("deviceId", deviceId.toString())
            .claim("tv", 0)
            .issuer("muhabbet-test")
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(newSecret.toByteArray()))
            .compact()

        assertNull(provider(newSecret, keyId = "k2").validateToken(token))
    }

    @Test
    fun `should reject a token with no kid at all`() {
        val token = Jwts.builder()
            .subject(userId.toString())
            .audience().add("muhabbet-app").and()
            .claim("deviceId", deviceId.toString())
            .claim("tv", 0)
            .issuer("muhabbet-test")
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(newSecret.toByteArray()))
            .compact()

        assertNull(provider(newSecret, keyId = "k2").validateToken(token))
    }

    @Test
    fun `should reject a token issued for another audience`() {
        val token = Jwts.builder()
            .header().keyId("k2").and()
            .subject(userId.toString())
            .audience().add("muhabbet-ops").and()
            .claim("deviceId", deviceId.toString())
            .claim("tv", 0)
            .issuer("muhabbet-test")
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(newSecret.toByteArray()))
            .compact()

        assertNull(provider(newSecret, keyId = "k2").validateToken(token))
    }

    @Test
    fun `should reject a token without a token epoch`() {
        // Without `tv` the REST filter would have nothing to compare against the device's epoch.
        val token = Jwts.builder()
            .header().keyId("k2").and()
            .subject(userId.toString())
            .audience().add("muhabbet-app").and()
            .claim("deviceId", deviceId.toString())
            .issuer("muhabbet-test")
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(newSecret.toByteArray()))
            .compact()

        assertNull(provider(newSecret, keyId = "k2").validateToken(token))
    }

    @Test
    fun `should carry kid, jti and the token epoch`() {
        val p = provider(newSecret, keyId = "k2")
        val first = p.generateAccessToken(userId, deviceId, tokenVersion = 4)
        val second = p.generateAccessToken(userId, deviceId, tokenVersion = 4)

        val parsed = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(newSecret.toByteArray())).build()
        val header = parsed.parseSignedClaims(first).header
        assertEquals("k2", header.keyId)
        assertNotNull(parsed.parseSignedClaims(first).payload.id)
        assertNotEquals(
            parsed.parseSignedClaims(first).payload.id,
            parsed.parseSignedClaims(second).payload.id,
            "jti must be unique per token"
        )
        assertEquals(4, p.validateToken(first)?.tokenVersion)
    }

    @Test
    fun `should refuse to start with two keys sharing a kid`() {
        val error = assertThrows<IllegalStateException> {
            provider(newSecret, keyId = "k1", previous = listOf(JwtVerificationKey("k1", oldSecret)))
        }
        assertTrue(error.message.orEmpty().contains("unique"))
    }

    @Test
    fun `should refuse to start with a weak retired key`() {
        val error = assertThrows<IllegalStateException> {
            provider(newSecret, keyId = "k2", previous = listOf(JwtVerificationKey("k1", "short")))
        }
        assertTrue(error.message.orEmpty().contains("too short"))
    }
}
