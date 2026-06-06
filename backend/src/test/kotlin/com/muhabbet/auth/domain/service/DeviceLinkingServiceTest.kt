package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.model.DeviceLinkStatus
import com.muhabbet.auth.domain.port.out.DeviceLinkSessionRepository
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DeviceLinkingServiceTest {

    private lateinit var sessionRepo: DeviceLinkSessionRepository
    private lateinit var deviceRepo: DeviceRepository
    private lateinit var service: DeviceLinkingService

    private val userId = UUID.randomUUID()
    private val primaryDeviceId = UUID.randomUUID()

    private fun primaryDevice() = Device(
        id = primaryDeviceId, userId = userId, platform = "android",
        deviceName = "Pixel", isPrimary = true
    )

    private fun pendingSession(token: String = "tok-123", expiresAt: Instant = Instant.now().plusSeconds(120)) =
        DeviceLinkSession(
            userId = userId, primaryDeviceId = primaryDeviceId,
            linkToken = token, expiresAt = expiresAt
        )

    @BeforeEach
    fun setUp() {
        sessionRepo = mockk(relaxed = true)
        deviceRepo = mockk(relaxed = true)
        service = DeviceLinkingService(sessionRepo, deviceRepo)

        every { sessionRepo.save(any()) } answers { firstArg() }
        every { deviceRepo.save(any()) } answers { firstArg() }
        every { deviceRepo.findById(primaryDeviceId) } returns primaryDevice()
        every { deviceRepo.findActiveByUserId(userId) } returns listOf(primaryDevice())
    }

    // ─── beginLink ───────────────────────────────────────

    @Test
    fun `beginLink issues a pending session with a fresh random token and expiry`() {
        val saved = slot<DeviceLinkSession>()
        every { sessionRepo.save(capture(saved)) } answers { firstArg() }

        val session = service.beginLink(userId, primaryDeviceId)

        assertEquals(DeviceLinkStatus.PENDING, session.status)
        assertTrue(session.linkToken.isNotBlank())
        // URL-safe base64, no padding/'+'/'/'.
        assertFalse(session.linkToken.contains('+') || session.linkToken.contains('/') || session.linkToken.contains('='))
        assertTrue(session.expiresAt.isAfter(Instant.now()))
        assertEquals(userId, saved.captured.userId)
        assertEquals(primaryDeviceId, saved.captured.primaryDeviceId)
    }

    @Test
    fun `beginLink rejects when the opener device is not owned by the user`() {
        every { deviceRepo.findById(primaryDeviceId) } returns primaryDevice().copy(userId = UUID.randomUUID())

        val ex = assertThrows(BusinessException::class.java) { service.beginLink(userId, primaryDeviceId) }
        assertEquals(ErrorCode.DEVICE_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `beginLink rejects when the companion-device cap is already reached`() {
        val companions = (1..4).map {
            Device(userId = userId, platform = "web", linkedByDeviceId = primaryDeviceId)
        }
        every { deviceRepo.findActiveByUserId(userId) } returns listOf(primaryDevice()) + companions

        val ex = assertThrows(BusinessException::class.java) { service.beginLink(userId, primaryDeviceId) }
        assertEquals(ErrorCode.DEVICE_LINK_LIMIT_REACHED, ex.errorCode)
    }

    @Test
    fun `two beginLink calls produce different tokens`() {
        val s1 = service.beginLink(userId, primaryDeviceId)
        val s2 = service.beginLink(userId, primaryDeviceId)
        assertTrue(s1.linkToken != s2.linkToken, "tokens must be unique per session")
    }

    // ─── completeLink ────────────────────────────────────

    @Test
    fun `completeLink registers a companion device and marks the session completed`() {
        every { sessionRepo.findByLinkToken("tok-123") } returns pendingSession()
        val savedSession = slot<DeviceLinkSession>()
        every { sessionRepo.save(capture(savedSession)) } answers { firstArg() }
        val savedDevice = slot<Device>()
        every { deviceRepo.save(capture(savedDevice)) } answers { firstArg() }

        val device = service.completeLink("tok-123", "web", "Chrome on macOS", "PUBLIC-BUNDLE-BLOB")

        // A companion device row owned by the same user is created.
        assertEquals(userId, device.userId)
        assertEquals("web", device.platform)
        assertTrue(device.isCompanion)
        assertFalse(device.isPrimary)
        assertEquals(primaryDeviceId, device.linkedByDeviceId)
        assertTrue(device.isActive)

        // Session is completed, links to the new device, and stores the OPAQUE public bundle only.
        assertEquals(DeviceLinkStatus.COMPLETED, savedSession.captured.status)
        assertEquals(device.id, savedSession.captured.linkedDeviceId)
        assertEquals("PUBLIC-BUNDLE-BLOB", savedSession.captured.publicBundle)
        assertNotNull(savedSession.captured.completedAt)
    }

    @Test
    fun `completeLink succeeds with no public bundle (crypto slice not yet shipped)`() {
        every { sessionRepo.findByLinkToken("tok-123") } returns pendingSession()
        val savedSession = slot<DeviceLinkSession>()
        every { sessionRepo.save(capture(savedSession)) } answers { firstArg() }

        val device = service.completeLink("tok-123", "web", "Web", publicBundle = null)

        assertTrue(device.isCompanion)
        assertNull(savedSession.captured.publicBundle)
        assertEquals(DeviceLinkStatus.COMPLETED, savedSession.captured.status)
    }

    @Test
    fun `completeLink rejects an unknown token`() {
        every { sessionRepo.findByLinkToken("nope") } returns null
        val ex = assertThrows(BusinessException::class.java) {
            service.completeLink("nope", "web", "Web", null)
        }
        assertEquals(ErrorCode.DEVICE_LINK_TOKEN_INVALID, ex.errorCode)
        verify(exactly = 0) { deviceRepo.save(any()) }
    }

    @Test
    fun `completeLink rejects a token that was already used`() {
        every { sessionRepo.findByLinkToken("tok-123") } returns
            pendingSession().copy(status = DeviceLinkStatus.COMPLETED)

        val ex = assertThrows(BusinessException::class.java) {
            service.completeLink("tok-123", "web", "Web", null)
        }
        assertEquals(ErrorCode.DEVICE_LINK_ALREADY_USED, ex.errorCode)
        verify(exactly = 0) { deviceRepo.save(any()) }
    }

    @Test
    fun `completeLink rejects an expired token and marks the session expired`() {
        every { sessionRepo.findByLinkToken("tok-123") } returns
            pendingSession(expiresAt = Instant.now().minusSeconds(1))
        val savedSession = slot<DeviceLinkSession>()
        every { sessionRepo.save(capture(savedSession)) } answers { firstArg() }

        val ex = assertThrows(BusinessException::class.java) {
            service.completeLink("tok-123", "web", "Web", null)
        }
        assertEquals(ErrorCode.DEVICE_LINK_EXPIRED, ex.errorCode)
        assertEquals(DeviceLinkStatus.EXPIRED, savedSession.captured.status)
        verify(exactly = 0) { deviceRepo.save(any()) }
    }

    @Test
    fun `completeLink rejects when the cap was filled concurrently`() {
        every { sessionRepo.findByLinkToken("tok-123") } returns pendingSession()
        val companions = (1..4).map { Device(userId = userId, platform = "web", linkedByDeviceId = primaryDeviceId) }
        every { deviceRepo.findActiveByUserId(userId) } returns listOf(primaryDevice()) + companions

        val ex = assertThrows(BusinessException::class.java) {
            service.completeLink("tok-123", "web", "Web", null)
        }
        assertEquals(ErrorCode.DEVICE_LINK_LIMIT_REACHED, ex.errorCode)
        verify(exactly = 0) { deviceRepo.save(any()) }
    }

    // ─── listDevices ─────────────────────────────────────

    @Test
    fun `listDevices returns only active devices, newest first`() {
        val old = Device(userId = userId, platform = "web", linkedByDeviceId = primaryDeviceId,
            createdAt = Instant.now().minusSeconds(100))
        val new = Device(userId = userId, platform = "desktop", linkedByDeviceId = primaryDeviceId,
            createdAt = Instant.now())
        every { deviceRepo.findActiveByUserId(userId) } returns listOf(old, new)

        val result = service.listDevices(userId)

        assertEquals(listOf(new.id, old.id), result.map { it.id })
    }

    // ─── revokeDevice ────────────────────────────────────

    @Test
    fun `revokeDevice tombstones a companion the user owns`() {
        val companionId = UUID.randomUUID()
        val companion = Device(id = companionId, userId = userId, platform = "web", linkedByDeviceId = primaryDeviceId)
        every { deviceRepo.findById(companionId) } returns companion
        val saved = slot<Device>()
        every { deviceRepo.save(capture(saved)) } answers { firstArg() }

        val revoked = service.revokeDevice(userId, companionId)

        assertFalse(revoked.isActive)
        assertNotNull(saved.captured.revokedAt)
    }

    @Test
    fun `revokeDevice refuses to revoke the primary device`() {
        every { deviceRepo.findById(primaryDeviceId) } returns primaryDevice()
        val ex = assertThrows(BusinessException::class.java) { service.revokeDevice(userId, primaryDeviceId) }
        assertEquals(ErrorCode.DEVICE_CANNOT_REVOKE_PRIMARY, ex.errorCode)
        verify(exactly = 0) { deviceRepo.save(any()) }
    }

    @Test
    fun `revokeDevice refuses a device owned by another user`() {
        val otherId = UUID.randomUUID()
        every { deviceRepo.findById(otherId) } returns
            Device(id = otherId, userId = UUID.randomUUID(), platform = "web", linkedByDeviceId = UUID.randomUUID())
        val ex = assertThrows(BusinessException::class.java) { service.revokeDevice(userId, otherId) }
        assertEquals(ErrorCode.DEVICE_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `revokeDevice is idempotent on an already-revoked device`() {
        val companionId = UUID.randomUUID()
        every { deviceRepo.findById(companionId) } returns Device(
            id = companionId, userId = userId, platform = "web",
            linkedByDeviceId = primaryDeviceId, revokedAt = Instant.now().minusSeconds(60)
        )

        val result = service.revokeDevice(userId, companionId)

        assertFalse(result.isActive)
        verify(exactly = 0) { deviceRepo.save(any()) } // no second write
    }
}
