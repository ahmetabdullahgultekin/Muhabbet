package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.port.`in`.LogoutUseCase
import com.muhabbet.auth.domain.port.`in`.RefreshTokenUseCase
import com.muhabbet.auth.domain.port.`in`.TokenResult
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * The life of a session after sign-in: rotating its refresh token, and ending it (#894).
 */
class TokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val sessionTokens: SessionTokens,
    private val transactions: TransactionRunner
) : RefreshTokenUseCase, LogoutUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun refresh(refreshToken: String): TokenResult = transactions.inTransaction {
        val tokenHash = sha256Hex(refreshToken)
        val record = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: throw BusinessException(ErrorCode.AUTH_TOKEN_INVALID)

        rejectionFor(record.revokedAt, record.expiresAt)?.let { throw BusinessException(it) }

        // Rotation: the presented token is spent whether or not the client receives the new one.
        refreshTokenRepository.revokeByTokenHash(tokenHash)

        // The admin flag is re-read from the user row on every refresh rather than carried over
        // from the old token, so a grant or a revoke takes effect within one access-token lifetime
        // instead of persisting until the holder logs out.
        val isAdmin = userRepository.findById(record.userId)?.isAdmin ?: false
        val tokens = sessionTokens.issue(record.userId, record.deviceId, isAdmin)

        log.info("Token refreshed: userId={}, deviceId={}", record.userId, record.deviceId)

        TokenResult(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            expiresIn = tokens.expiresIn,
            userId = record.userId.toString(),
            deviceId = record.deviceId.toString()
        )
    }

    /** Revocation is checked before expiry, so a stolen-and-revoked token says so even once stale. */
    private fun rejectionFor(revokedAt: Instant?, expiresAt: Instant): ErrorCode? = when {
        revokedAt != null -> ErrorCode.AUTH_TOKEN_REVOKED
        expiresAt.isBefore(Instant.now()) -> ErrorCode.AUTH_TOKEN_EXPIRED
        else -> null
    }

    override fun logout(userId: UUID, deviceId: UUID) {
        transactions.inTransaction { refreshTokenRepository.revokeAllForDevice(userId, deviceId) }
        log.info("Logout: userId={}, deviceId={}", userId, deviceId)
    }
}
