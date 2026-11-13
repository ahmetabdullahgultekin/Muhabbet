package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.RefreshDecision
import com.muhabbet.auth.domain.model.RefreshRejection
import com.muhabbet.auth.domain.model.RefreshTokenState
import com.muhabbet.auth.domain.model.SessionStanding
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.model.onPresented
import com.muhabbet.auth.domain.port.`in`.LogoutUseCase
import com.muhabbet.auth.domain.port.`in`.RefreshTokenUseCase
import com.muhabbet.auth.domain.port.`in`.TokenResult
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Refresh-token rotation with reuse detection, and logout (#880).
 *
 * The decision of what to do with a presented token is the pure [onPresented]; this class reads the
 * inputs, executes the decision's effects, and owns the transaction boundary. The boundary is a
 * [TransactionRunner] rather than `@Transactional` because a refused refresh still has to **commit**
 * its effect — revoking a family is the whole point of detecting reuse — and an exception thrown out
 * of a `@Transactional` method would roll that revocation back. So the transaction returns an
 * outcome, commits, and only then is a refusal turned into an exception.
 */
open class SessionService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository,
    private val sessionRevocation: SessionRevocation,
    private val jwtProvider: JwtProvider,
    private val transactions: TransactionRunner,
    private val refreshTokenExpirySeconds: Long
) : RefreshTokenUseCase, LogoutUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    private sealed interface Outcome {
        data class Issued(val result: TokenResult) : Outcome
        data class Refused(val errorCode: ErrorCode) : Outcome
    }

    override fun refresh(refreshToken: String): TokenResult {
        val tokenHash = AuthService.sha256(refreshToken)
        return when (val outcome = transactions.inTransaction { refreshInTransaction(tokenHash) }) {
            is Outcome.Issued -> outcome.result
            is Outcome.Refused -> throw BusinessException(outcome.errorCode)
        }
    }

    private fun refreshInTransaction(tokenHash: String): Outcome {
        val now = Instant.now()
        val record = refreshTokenRepository.findByTokenHash(tokenHash)
            ?: return Outcome.Refused(ErrorCode.AUTH_TOKEN_INVALID)

        val state = RefreshTokenState.of(record.revokedAt, record.rotatedAt, record.expiresAt, now)
        val user = userRepository.findById(record.userId)
        val device = deviceRepository.findById(record.deviceId)
        val standing = if (user?.status == UserStatus.ACTIVE && device?.isActive == true && device.userId == user.id) {
            SessionStanding.LIVE
        } else {
            SessionStanding.ENDED
        }

        return when (val decision = state.onPresented(standing)) {
            RefreshDecision.Rotate -> rotate(record, now)
            is RefreshDecision.RevokeFamily -> {
                sessionRevocation.endFamily(record.familyId, record.deviceId)
                logRevocation(record, decision.reuseDetected)
                Outcome.Refused(ErrorCode.AUTH_TOKEN_REVOKED)
            }
            is RefreshDecision.Reject -> Outcome.Refused(
                when (decision.reason) {
                    RefreshRejection.REVOKED -> ErrorCode.AUTH_TOKEN_REVOKED
                    RefreshRejection.EXPIRED -> ErrorCode.AUTH_TOKEN_EXPIRED
                }
            )
        }
    }

    /**
     * Retires the token and issues its successor — unless another request retired it first, which
     * is a replay racing the legitimate client (or the reverse) and is answered as reuse.
     */
    private fun rotate(record: RefreshTokenRecord, now: Instant): Outcome {
        if (!refreshTokenRepository.markRotated(record.tokenHash, now)) {
            sessionRevocation.endFamily(record.familyId, record.deviceId)
            logRevocation(record, reuseDetected = true)
            return Outcome.Refused(ErrorCode.AUTH_TOKEN_REVOKED)
        }

        val newRefreshToken = jwtProvider.generateRefreshToken()
        refreshTokenRepository.save(
            RefreshTokenRecord(
                userId = record.userId,
                deviceId = record.deviceId,
                tokenHash = AuthService.sha256(newRefreshToken),
                expiresAt = now.plusSeconds(refreshTokenExpirySeconds),
                familyId = record.familyId
            )
        )
        // Re-read after the rotation so the access token carries the epoch as it is now, not as it
        // was before a concurrent logout bumped it.
        val tokenVersion = deviceRepository.findById(record.deviceId)?.tokenVersion
            ?: return Outcome.Refused(ErrorCode.AUTH_TOKEN_REVOKED)

        log.info("Token refreshed: userId={}, deviceId={}", record.userId, record.deviceId)
        return Outcome.Issued(
            TokenResult(
                accessToken = jwtProvider.generateAccessToken(record.userId, record.deviceId, tokenVersion),
                refreshToken = newRefreshToken,
                expiresIn = jwtProvider.accessTokenExpirySeconds,
                userId = record.userId.toString(),
                deviceId = record.deviceId.toString()
            )
        )
    }

    private fun logRevocation(record: RefreshTokenRecord, reuseDetected: Boolean) {
        if (reuseDetected) {
            log.warn(
                "Refresh token reuse detected; family revoked: userId={}, deviceId={}, familyId={}",
                record.userId, record.deviceId, record.familyId
            )
        } else {
            log.info(
                "Refresh refused, session ended (device revoked or account inactive): userId={}, deviceId={}",
                record.userId, record.deviceId
            )
        }
    }

    override fun logout(userId: UUID, deviceId: UUID) {
        transactions.inTransaction { sessionRevocation.endDeviceSessions(userId, deviceId) }
        log.info("Logout: userId={}, deviceId={}", userId, deviceId)
    }
}
