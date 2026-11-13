package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DevicePlatform
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.AuthTestFixtures
import com.muhabbet.shared.InlineTransactionRunner
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * Refresh-token rotation, reuse detection and logout (#880), against an in-memory token store whose
 * conditional rotation behaves like the SQL one: of two presentations of one live token, exactly one
 * sees `true`. The concurrent version of the same claim runs against PostgreSQL in
 * `AuthSessionHardeningIntegrationTest`.
 */
class SessionServiceTest {

    /** The token table, with `markRotated` as conditional as its UPDATE. */
    private class InMemoryRefreshTokens : RefreshTokenRepository {
        val rows = mutableListOf<RefreshTokenRecord>()

        @Synchronized override fun save(record: RefreshTokenRecord) = record.also { rows += it }
        @Synchronized override fun findByTokenHash(tokenHash: String) = rows.firstOrNull { it.tokenHash == tokenHash }
        @Synchronized override fun findByUserId(userId: UUID) = rows.filter { it.userId == userId }

        @Synchronized override fun revokeAllForDevice(userId: UUID, deviceId: UUID) =
            update({ it.userId == userId && it.deviceId == deviceId && it.revokedAt == null }) { it.copy(revokedAt = Instant.now()) }

        @Synchronized override fun revokeAllForUser(userId: UUID) =
            update({ it.userId == userId && it.revokedAt == null }) { it.copy(revokedAt = Instant.now()) }

        @Synchronized override fun markRotated(tokenHash: String, at: Instant): Boolean {
            val index = rows.indexOfFirst { it.tokenHash == tokenHash && it.revokedAt == null && it.expiresAt.isAfter(at) }
            if (index < 0) return false
            rows[index] = rows[index].copy(revokedAt = at, rotatedAt = at)
            return true
        }

        @Synchronized override fun revokeFamily(familyId: UUID, at: Instant) =
            update({ it.familyId == familyId && it.revokedAt == null }) { it.copy(revokedAt = at) }

        private fun update(match: (RefreshTokenRecord) -> Boolean, change: (RefreshTokenRecord) -> RefreshTokenRecord) {
            rows.replaceAll { if (match(it)) change(it) else it }
        }

        fun live(familyId: UUID) = rows.filter { it.familyId == familyId && it.revokedAt == null }
    }

    private val jwtProvider = AuthTestFixtures.jwtProvider()
    private lateinit var tokens: InMemoryRefreshTokens
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var userRepository: UserRepository
    private lateinit var service: SessionService

    private val userId = UUID.randomUUID()
    private val device = Device(userId = userId, platform = DevicePlatform.ANDROID, tokenVersion = 2)
    private var user = User(id = userId, phoneNumber = "+905000000001", status = UserStatus.ACTIVE)

    @BeforeEach
    fun setUp() {
        tokens = InMemoryRefreshTokens()
        deviceRepository = mockk(relaxed = true)
        userRepository = mockk()
        every { deviceRepository.findById(device.id) } returns device
        every { userRepository.findById(userId) } answers { user }
        service = SessionService(
            refreshTokenRepository = tokens,
            deviceRepository = deviceRepository,
            userRepository = userRepository,
            sessionRevocation = SessionRevocation(tokens, deviceRepository),
            jwtProvider = jwtProvider,
            transactions = InlineTransactionRunner(),
            refreshTokenExpirySeconds = 3600
        )
    }

    /** A sign-in: a fresh family rooted at one token. Returns the raw token. */
    private fun signIn(): String {
        val raw = jwtProvider.generateRefreshToken()
        tokens.save(
            RefreshTokenRecord(
                userId = userId,
                deviceId = device.id,
                tokenHash = AuthService.sha256(raw),
                expiresAt = Instant.now().plusSeconds(3600)
            )
        )
        return raw
    }

    private fun familyOf(raw: String) = tokens.findByTokenHash(AuthService.sha256(raw))?.familyId
        ?: error("no such token")

    @Test
    fun `should rotate within the same family and carry the device epoch`() {
        val raw = signIn()

        val result = service.refresh(raw)

        assertNotEquals(raw, result.refreshToken)
        assertEquals(familyOf(raw), familyOf(result.refreshToken))
        assertEquals(2, jwtProvider.validateToken(result.accessToken)?.tokenVersion)
        assertEquals(userId.toString(), result.userId)
        assertEquals(device.id.toString(), result.deviceId)
    }

    @Test
    fun `should revoke the whole family when a rotated token is presented again`() {
        // The attacker and the victim each hold the same token; whoever is second trips the alarm.
        val stolen = signIn()
        val family = familyOf(stolen)
        val successor = service.refresh(stolen).refreshToken

        val ex = assertThrows<BusinessException> { service.refresh(stolen) }

        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, ex.errorCode)
        assertTrue(tokens.live(family).isEmpty(), "the successor survived the reuse")
        assertThrows<BusinessException> { service.refresh(successor) }
        verify { deviceRepository.bumpTokenVersion(device.id) }
    }

    @Test
    fun `should let exactly one of two racing presentations of one token through`() {
        // Both read the token as Active; the conditional rotation decides between them.
        val raw = signIn()
        val family = familyOf(raw)
        // This presentation read the token as Active, then lost the UPDATE to the other one.
        val racing = object : RefreshTokenRepository by tokens {
            override fun markRotated(tokenHash: String, at: Instant) = false
        }
        val loser = SessionService(
            racing, deviceRepository, userRepository, SessionRevocation(racing, deviceRepository),
            jwtProvider, InlineTransactionRunner(), 3600
        )

        val ex = assertThrows<BusinessException> { loser.refresh(raw) }

        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, ex.errorCode)
        assertTrue(tokens.live(family).isEmpty(), "the losing presentation did not revoke the family")
    }

    @Test
    fun `should refuse and revoke when the device has been revoked`() {
        val raw = signIn()
        every { deviceRepository.findById(device.id) } returns device.copy(revokedAt = Instant.now())

        val ex = assertThrows<BusinessException> { service.refresh(raw) }

        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, ex.errorCode)
        assertTrue(tokens.live(familyOf(raw)).isEmpty())
    }

    @Test
    fun `should refuse when the account is suspended`() {
        val raw = signIn()
        user = user.copy(status = UserStatus.SUSPENDED)

        val ex = assertThrows<BusinessException> { service.refresh(raw) }

        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, ex.errorCode)
    }

    @Test
    fun `should refuse after logout and leave other devices alone`() {
        val raw = signIn()
        val otherDevice = UUID.randomUUID()
        val otherRaw = jwtProvider.generateRefreshToken()
        tokens.save(
            RefreshTokenRecord(
                userId = userId, deviceId = otherDevice,
                tokenHash = AuthService.sha256(otherRaw), expiresAt = Instant.now().plusSeconds(3600)
            )
        )

        service.logout(userId, device.id)

        assertEquals(ErrorCode.AUTH_TOKEN_REVOKED, assertThrows<BusinessException> { service.refresh(raw) }.errorCode)
        assertNotNull(tokens.findByTokenHash(AuthService.sha256(otherRaw))?.takeIf { it.revokedAt == null })
        verify { deviceRepository.bumpTokenVersion(device.id) }
        verify(exactly = 0) { deviceRepository.bumpTokenVersion(otherDevice) }
    }

    @Test
    fun `should answer an unknown token as invalid and an expired one as expired`() {
        assertEquals(ErrorCode.AUTH_TOKEN_INVALID, assertThrows<BusinessException> { service.refresh("nope") }.errorCode)

        val raw = jwtProvider.generateRefreshToken()
        tokens.save(
            RefreshTokenRecord(
                userId = userId, deviceId = device.id,
                tokenHash = AuthService.sha256(raw), expiresAt = Instant.now().minusSeconds(1)
            )
        )
        assertEquals(ErrorCode.AUTH_TOKEN_EXPIRED, assertThrows<BusinessException> { service.refresh(raw) }.errorCode)
    }
}
