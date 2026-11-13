package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.shared.security.JwtProvider
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Minting the pair of tokens a session runs on: a short-lived access token and a rotating refresh
 * token whose hash — never the token — is stored.
 *
 * Shared by sign-in ([PhoneSignIn]) and rotation ([TokenService]) rather than written twice, which
 * is how it was inside the old `AuthService` (#894): two copies of "generate, hash, store with an
 * expiry" that had to agree on the hash and the lifetime.
 */
class SessionTokens(
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val refreshTokenExpirySeconds: Long
) {

    data class Issued(val accessToken: String, val refreshToken: String, val expiresIn: Long)

    fun issue(userId: UUID, deviceId: UUID, isAdmin: Boolean): Issued {
        val accessToken = jwtProvider.generateAccessToken(userId, deviceId, isAdmin)
        val refreshToken = jwtProvider.generateRefreshToken()

        refreshTokenRepository.save(
            RefreshTokenRecord(
                userId = userId,
                deviceId = deviceId,
                tokenHash = sha256Hex(refreshToken),
                expiresAt = Instant.now().plusSeconds(refreshTokenExpirySeconds)
            )
        )

        return Issued(accessToken, refreshToken, jwtProvider.accessTokenExpirySeconds)
    }
}

/** Lower-case hex SHA-256 — the stored form of refresh tokens and of phone numbers for contact sync. */
internal fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}
