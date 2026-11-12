package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.SessionRevocationReason
import com.muhabbet.auth.domain.port.out.DeviceLogoutPort
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.SessionRevocationPort
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class LogoutServiceTest {

    private val refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true)
    private val sessionRevocationPort = mockk<SessionRevocationPort>(relaxed = true)
    private val deviceLogoutPort = mockk<DeviceLogoutPort>(relaxed = true)
    private val now: Instant = Instant.parse("2026-11-12T10:00:00.500Z")
    private val service = LogoutService(
        refreshTokenRepository,
        deviceLogoutPort,
        sessionRevocationPort,
        Clock.fixed(now, ZoneOffset.UTC)
    )

    private val userId = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()

    @Test
    fun `should revoke the device's refresh tokens when it logs out`() {
        service.logout(userId, deviceId)

        verify(exactly = 1) { refreshTokenRepository.revokeAllForDevice(userId, deviceId) }
    }

    @Test
    fun `should close only that device's live sessions when it logs out`() {
        service.logout(userId, deviceId)

        verify(exactly = 1) {
            sessionRevocationPort.revokeDeviceSessions(userId, deviceId, SessionRevocationReason.LOGOUT)
        }
        verify(exactly = 0) { sessionRevocationPort.revokeUserSessions(any(), any()) }
    }

    @Test
    fun `should record the logout instant on the device so its current access token is refused when it logs out`() {
        service.logout(userId, deviceId)

        verify(exactly = 1) { deviceLogoutPort.recordLogout(userId, deviceId, now) }
    }
}
