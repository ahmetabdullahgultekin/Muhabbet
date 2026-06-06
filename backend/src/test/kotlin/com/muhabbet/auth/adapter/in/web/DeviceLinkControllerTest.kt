package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.port.`in`.LinkDeviceUseCase
import com.muhabbet.shared.config.MultiDeviceProperties
import com.muhabbet.shared.dto.DeviceLinkCompleteRequest
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID

class DeviceLinkControllerTest {

    private lateinit var useCase: LinkDeviceUseCase
    private val userId = UUID.randomUUID()
    private val deviceId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        useCase = mockk(relaxed = true)
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(claims, null, emptyList())
    }

    private fun controller(enabled: Boolean) =
        DeviceLinkController(useCase, MultiDeviceProperties(enabled = enabled))

    private fun device() = Device(userId = userId, platform = "web", linkedByDeviceId = deviceId, displayName = "Web")

    // ─── Flag gating (the reversibility guarantee) ───────

    @Test
    fun `begin returns 403 DEVICE_LINKING_DISABLED when the flag is OFF`() {
        val ex = assertThrows(BusinessException::class.java) { controller(enabled = false).begin() }
        assertEquals(ErrorCode.DEVICE_LINKING_DISABLED, ex.errorCode)
        verify(exactly = 0) { useCase.beginLink(any(), any()) }
    }

    @Test
    fun `complete returns 403 when the flag is OFF and never touches the use case`() {
        val ex = assertThrows(BusinessException::class.java) {
            controller(enabled = false).complete(DeviceLinkCompleteRequest("tok", "web", "Web", null))
        }
        assertEquals(ErrorCode.DEVICE_LINKING_DISABLED, ex.errorCode)
        verify(exactly = 0) { useCase.completeLink(any(), any(), any(), any()) }
    }

    @Test
    fun `list returns 403 when the flag is OFF`() {
        val ex = assertThrows(BusinessException::class.java) { controller(enabled = false).list() }
        assertEquals(ErrorCode.DEVICE_LINKING_DISABLED, ex.errorCode)
    }

    @Test
    fun `revoke returns 403 when the flag is OFF`() {
        val ex = assertThrows(BusinessException::class.java) { controller(enabled = false).revoke(deviceId) }
        assertEquals(ErrorCode.DEVICE_LINKING_DISABLED, ex.errorCode)
        verify(exactly = 0) { useCase.revokeDevice(any(), any()) }
    }

    // ─── Happy paths when the flag is ON ─────────────────

    @Test
    fun `begin returns 201 with the QR token when the flag is ON`() {
        every { useCase.beginLink(userId, deviceId) } returns DeviceLinkSession(
            userId = userId, primaryDeviceId = deviceId,
            linkToken = "QR-TOKEN", expiresAt = Instant.now().plusSeconds(120)
        )

        val response = controller(enabled = true).begin()

        assertEquals(201, response.statusCode.value())
        assertEquals("QR-TOKEN", response.body?.data?.linkToken)
        verify { useCase.beginLink(userId, deviceId) }
    }

    @Test
    fun `complete passes the request through and returns the linked device`() {
        every { useCase.completeLink("tok", "web", "Web", "BUNDLE") } returns device()

        val response = controller(enabled = true)
            .complete(DeviceLinkCompleteRequest("tok", "web", "Web", "BUNDLE"))

        assertEquals(201, response.statusCode.value())
        assertEquals("web", response.body?.data?.platform)
        assertEquals(true, response.body?.data?.isCompanion)
        verify { useCase.completeLink("tok", "web", "Web", "BUNDLE") }
    }

    @Test
    fun `list returns the user's active devices when the flag is ON`() {
        every { useCase.listDevices(userId) } returns listOf(device())

        val response = controller(enabled = true).list()

        assertEquals(200, response.statusCode.value())
        assertEquals(1, response.body?.data?.size)
        verify { useCase.listDevices(userId) }
    }

    @Test
    fun `revoke returns the tombstoned device when the flag is ON`() {
        val revoked = device().copy(revokedAt = Instant.now())
        every { useCase.revokeDevice(userId, revoked.id) } returns revoked

        val response = controller(enabled = true).revoke(revoked.id)

        assertEquals(200, response.statusCode.value())
        assertEquals(true, response.body?.data?.revoked)
        verify { useCase.revokeDevice(userId, revoked.id) }
    }
}
