package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.SessionRevocationReason
import com.muhabbet.auth.domain.port.`in`.LogoutUseCase
import com.muhabbet.auth.domain.port.out.DeviceLogoutPort
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.SessionRevocationPort
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Signing a device out (#747). Three things, all of which have to happen for "signed out" to mean
 * anything:
 *  - its refresh tokens stop working, so no new access token can be minted;
 *  - the logout instant is recorded on the device, so the access token it already holds — valid
 *    for up to its full lifetime — is refused from now on by the REST filter and the WebSocket
 *    handshake (without this a reconnect loop or a stolen copy got straight back in);
 *  - the WebSocket it already has open is closed, after the commit — see [SessionRevocationPort].
 */
open class LogoutService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val deviceLogoutPort: DeviceLogoutPort,
    private val sessionRevocationPort: SessionRevocationPort,
    private val clock: Clock = Clock.systemUTC()
) : LogoutUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun logout(userId: UUID, deviceId: UUID) {
        refreshTokenRepository.revokeAllForDevice(userId, deviceId)
        deviceLogoutPort.recordLogout(userId, deviceId, Instant.now(clock))
        sessionRevocationPort.revokeDeviceSessions(userId, deviceId, SessionRevocationReason.LOGOUT)
        log.info("Logout: userId={}, deviceId={}", userId, deviceId)
    }
}
