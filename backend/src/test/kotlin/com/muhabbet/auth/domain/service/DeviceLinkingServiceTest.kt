package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DeviceLinkCompletion
import com.muhabbet.auth.domain.model.DeviceLinkSession
import com.muhabbet.auth.domain.model.DeviceLinkStatus
import com.muhabbet.auth.domain.model.IssuedTokens
import com.muhabbet.auth.domain.port.out.DeviceLinkSessionRepository
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.UserRepository
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class DeviceLinkingServiceTest {

    private lateinit var sessionRepo: DeviceLinkSessionRepository
    private lateinit var deviceRepo: DeviceRepository
    private lateinit var userRepo: UserRepository
    private lateinit var tokenIssuer: DeviceTokenIssuer
    private lateinit var service: DeviceLinkingService

    private val now = Instant.parse("2026-11-13T12:00:00Z")
    private val userId = UUID.randomUUID()
    private val primaryDeviceId = UUID.randomUUID()
    private val tokens = IssuedTokens("access", "refresh", 900)

    private fun primaryDevice() = Device(
        id = primaryDeviceId, userId = userId, platform = "android",
        deviceName = "Pixel", isPrimary = true
    )

    private fun pendingSession(token: String = "tok-123", expiresAt: Instant = now.plusSeconds(120)) =
        DeviceLinkSession(
            userId = userId, primaryDeviceId = primaryDeviceId,
            tokenHash = AuthService.sha256(token), expiresAt = expiresAt
        )

    @BeforeEach
    fun setUp() {
        sessionRepo = mockk(relaxed = true)
        deviceRepo = mockk(relaxed = true)
        userRepo = mockk(relaxed = true)
        tokenIssuer = mockk()
        service = DeviceLinkingService(sessionRepo, deviceRepo, userRepo, tokenIssuer, Clock.fixed(now, ZoneOffset.UTC))

        every { sessionRepo.save(any()) } answers { firstArg() }
        every { sessionRepo.claimCompletion(any(), any()) } returns true
        every { deviceRepo.save(any()) } answers { firstArg() }
        every { deviceRepo.findById(primaryDeviceId) } returns primaryDevice()
        every { deviceRepo.findActiveByUserId(userId) } returns listOf(primaryDevice())
        every { userRepo.findById(userId) } returns null
        every { tokenIssuer.issue(any(), any(), any()) } returns tokens
    }

    // ─── transitions (pure) ──────────────────────────────

    @Test
    fun `a pending unexpired session completes`() {
        val decision = pendingSession().complete(now, "web", "Chrome", null)

        val completed = (decision as DeviceLinkCompletion.Completed).session
        assertEquals(DeviceLinkStatus.COMPLETED, completed.status)
        assertEquals(now, completed.completedAt)
    }

    @Test
    fun `an expired session reads EXPIRED without a write and cannot complete`() {
        val session = pendingSession(expiresAt = now.minusSeconds(1))

        assertEquals(DeviceLinkStatus.EXPIRED, session.effectiveStatus(now))
        assertEquals(DeviceLinkCompletion.Expired, session.complete(now, "web", null, null))
    }

    @Test
    fun `a session expiring exactly now is expired`() {
        assertEquals(DeviceLinkCompletion.Expired, pendingSession(expiresAt = now).complete(now, "web", null, null))
    }

    @Test
    fun `a completed or cancelled session cannot complete again`() {
        listOf(DeviceLinkStatus.COMPLETED, DeviceLinkStatus.CANCELLED).forEach { status ->
            assertEquals(
                DeviceLinkCompletion.AlreadyUsed,
                pendingSession().copy(status = status).complete(now, "web", null, null)
            )
        }
    }

    // ─── beginLink ───────────────────────────────────────

    @Test
    fun `beginLink stores only the hash of a fresh random token`() {
        val saved = slot<DeviceLinkSession>()
        every { sessionRepo.save(capture(saved)) } answers { firstArg() }

        val offer = service.beginLink(userId, primaryDeviceId)

        assertEquals(DeviceLinkStatus.PENDING, offer.session.status)
        // URL-safe base64, no padding/'+'/'/'.
        assertFalse(offer.linkToken.contains('+') || offer.linkToken.contains('/') || offer.linkToken.contains('='))
        assertEquals(32, offer.linkToken.length, "24 random bytes encode to 32 base64url characters")
        assertEquals(AuthService.sha256(offer.linkToken), saved.captured.tokenHash)
        assertEquals(now.plusSeconds(120), offer.session.expiresAt)
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
    fun `completeLink registers a companion, claims the session and issues the companion its tokens`() {
        every { sessionRepo.findByTokenHash(AuthService.sha256("tok-123")) } returns pendingSession()
        val claimed = slot<DeviceLinkSession>()
        every { sessionRepo.claimCompletion(capture(claimed), now) } returns true

        val result = service.completeLink("tok-123", "web", "Chrome on macOS", "PUBLIC-BUNDLE-BLOB")
        val device = result.device

        // A companion device row owned by the same user is created.
        assertEquals(userId, device.userId)
        assertEquals("web", device.platform)
        assertTrue(device.isCompanion)
        assertFalse(device.isPrimary)
        assertEquals(primaryDeviceId, device.linkedByDeviceId)
        assertTrue(device.isActive)

        // Session is completed, links to the new device, and stores the OPAQUE public bundle only.
        assertEquals(DeviceLinkStatus.COMPLETED, claimed.captured.status)
        assertEquals(device.id, claimed.captured.linkedDeviceId)
        assertEquals("PUBLIC-BUNDLE-BLOB", claimed.captured.publicBundle)
        assertNotNull(claimed.captured.completedAt)

        // The companion can authenticate as itself.
        assertEquals(tokens, result.tokens)
        verify { tokenIssuer.issue(userId, device.id, false) }
    }

    @Test
    fun `completeLink succeeds with no public bundle (crypto slice not yet shipped)`() {
        every { sessionRepo.findByTokenHash(AuthService.sha256("tok-123")) } returns pendingSession()
        val claimed = slot<DeviceLinkSession>()
        every { sessionRepo.claimCompletion(capture(claimed), now) } returns true

        val result = service.completeLink("tok-123", "web", "Web", publicBundle = null)

        assertTrue(result.device.isCompanion)
        assertNull(claimed.captured.publicBundle)
        assertEquals(DeviceLinkStatus.COMPLETED, claimed.captured.status)
    }

    @Test
    fun `completeLink rejects an unknown token`() {
        every { sessionRepo.findByTokenHash(any()) } returns null
        val ex = assertThrows(BusinessException::class.java) {
            service.completeLink("nope", "web", "Web", null)
        }
        assertEquals(ErrorCode.DEVICE_LINK_TOKEN_INVALID, ex.errorCode)
        verify(exactly = 0) { deviceRepo.save(any()) }
    }

    @Test
    fun `completeLink rejects a token that was already used`() {
        every { sessionRepo.findByTokenHash(AuthService.sha256("tok-123")) } returns
            pendingSession().copy(status = DeviceLinkStatus.COMPLETED)

        val ex = assertThrows(BusinessException::class.java) {
            service.completeLink("tok-123", "web", "Web", null)
        }
        assertEquals(ErrorCode.DEVICE_LINK_ALREADY_USED, ex.errorCode)
        verify(exactly = 0) { deviceRepo.save(any()) }
    }

    @Test
    fun `completeLink rejects an expired token without writing anything`() {
        every { sessionRepo.findByTokenHash(AuthService.sha256("tok-123")) } returns
            pendingSession(expiresAt = now.minusSeconds(1))

        val ex = assertThrows(BusinessException::class.java) {
            service.completeLink("tok-123", "web", "Web", null)
        }
        assertEquals(ErrorCode.DEVICE_LINK_EXPIRED, ex.errorCode)
        verify(exactly = 0) { deviceRepo.save(any()) }
        verify(exactly = 0) { sessionRepo.save(any()) }
        verify(exactly = 0) { sessionRepo.claimCompletion(any(), any()) }
    }

    @Test
    fun `completeLink refuses the loser of a concurrent claim and issues it nothing`() {
        every { sessionRepo.findByTokenHash(AuthService.sha256("tok-123")) } returns pendingSession()
        every { sessionRepo.claimCompletion(any(), any()) } returns false

        val ex = assertThrows(BusinessException::class.java) {
            service.completeLink("tok-123", "web", "Web", null)
        }
        assertEquals(ErrorCode.DEVICE_LINK_ALREADY_USED, ex.errorCode)
        verify(exactly = 0) { tokenIssuer.issue(any(), any(), any()) }
    }

    @Test
    fun `completeLink rejects when the cap was filled concurrently`() {
        every { sessionRepo.findByTokenHash(AuthService.sha256("tok-123")) } returns pendingSession()
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
