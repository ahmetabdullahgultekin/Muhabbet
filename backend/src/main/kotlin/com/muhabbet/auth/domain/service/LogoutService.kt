package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.SessionRevocationReason
import com.muhabbet.auth.domain.port.`in`.LogoutUseCase
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.SessionRevocationPort
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Signing a device out: its refresh tokens stop working, and so does the WebSocket it already has
 * open (#747). Before, only the first half happened, and the socket went on delivering messages to
 * a device that had signed out until the socket happened to drop.
 *
 * The socket close is requested inside the transaction but carried out after it commits — see
 * [SessionRevocationPort].
 */
open class LogoutService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val sessionRevocationPort: SessionRevocationPort
) : LogoutUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun logout(userId: UUID, deviceId: UUID) {
        refreshTokenRepository.revokeAllForDevice(userId, deviceId)
        sessionRevocationPort.revokeDeviceSessions(userId, deviceId, SessionRevocationReason.LOGOUT)
        log.info("Logout: userId={}, deviceId={}", userId, deviceId)
    }
}
