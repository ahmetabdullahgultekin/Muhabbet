package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.IssuedTokens
import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.shared.security.JwtProvider
import java.time.Clock
import java.util.UUID

/**
 * Mints an access token and a stored, hashed refresh token for a device — the one way a device
 * gets credentials.
 *
 * Extracted for #884: completing a device link created the companion's device row and then issued
 * nothing, so the companion had no way to authenticate. Sign-in, refresh and linking now all mint
 * through this, so the three cannot drift on hashing or expiry. Must run inside the caller's
 * transaction: the refresh-token row belongs to the same unit of work as whatever created the device.
 */
class DeviceTokenIssuer(
    private val jwtProvider: JwtProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val refreshTokenExpirySeconds: Long,
    private val clock: Clock = Clock.systemUTC()
) {
    fun issue(userId: UUID, deviceId: UUID, isAdmin: Boolean): IssuedTokens {
        val accessToken = jwtProvider.generateAccessToken(userId, deviceId, isAdmin)
        val refreshToken = jwtProvider.generateRefreshToken()
        refreshTokenRepository.save(
            RefreshTokenRecord(
                userId = userId,
                deviceId = deviceId,
                tokenHash = AuthService.sha256(refreshToken),
                expiresAt = clock.instant().plusSeconds(refreshTokenExpirySeconds)
            )
        )
        return IssuedTokens(accessToken, refreshToken, jwtProvider.accessTokenExpirySeconds)
    }
}
