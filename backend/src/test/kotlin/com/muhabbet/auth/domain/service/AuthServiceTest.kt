package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRecord
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import org.springframework.mock.env.MockEnvironment
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant
import java.util.UUID

class AuthServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var otpRepository: OtpRepository
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var phoneHashRepository: PhoneHashRepository
    private lateinit var otpSender: OtpSender
    private lateinit var jwtProvider: JwtProvider
    private lateinit var passwordEncoder: BCryptPasswordEncoder
    private lateinit var authService: AuthService

    private val validPhone = "+905321234567"
    private val validOtp = "123456"

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        otpRepository = mockk()
        deviceRepository = mockk()
        refreshTokenRepository = mockk()
        phoneHashRepository = mockk()
        otpSender = mockk()
        passwordEncoder = BCryptPasswordEncoder()

        val jwtProperties = JwtProperties(
            secret = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256",
            accessTokenExpiry = 900,
            refreshTokenExpiry = 2592000,
            issuer = "muhabbet-test"
        )
        jwtProvider = JwtProvider(jwtProperties, MockEnvironment())

        authService = AuthService(
            userRepository = userRepository,
            otpRepository = otpRepository,
            deviceRepository = deviceRepository,
            refreshTokenRepository = refreshTokenRepository,
            phoneHashRepository = phoneHashRepository,
            otpSender = otpSender,
            jwtProvider = jwtProvider,
            passwordEncoder = passwordEncoder,
            otpLength = 6,
            otpExpirySeconds = 300,
            otpCooldownSeconds = 60,
            otpMaxAttempts = 5,
            refreshTokenExpirySeconds = 2592000
        ,
            otpQuotaPort = mockk<com.muhabbet.auth.domain.port.out.OtpQuotaPort>().also {
            // Relaxed mocks answer false for Boolean, which would make every test here
            // fail on the quota rather than on what it is testing. The quota itself is
            // covered by RedisOtpQuotaAdapterTest against a real Redis.
            io.mockk.every { it.tryConsume(any()) } returns true
        },
            firebaseTokenVerifier = com.muhabbet.shared.NoFirebaseTokenVerifier,
            twoStepAttemptRepository = com.muhabbet.shared.InMemoryTwoStepAttemptRepository()
        )
    }

    // ─── requestOtp ─────────────────────────────────────

    @Test
    fun `should return OtpResult when valid phone and no cooldown`() {
        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns null
        every { otpRepository.save(any()) } answers { firstArg() }
        every { otpSender.send(validPhone, any()) } returns Unit

        val result = authService.requestOtp(validPhone)

        assertEquals(300, result.ttlSeconds)
        assertEquals(60, result.retryAfterSeconds)
        verify { otpRepository.save(any()) }
        verify { otpSender.send(validPhone, any()) }
    }

    @Test
    fun `should throw AUTH_INVALID_PHONE when phone is invalid`() {
        val ex = assertThrows<BusinessException> {
            authService.requestOtp("12345")
        }
        assertEquals(ErrorCode.AUTH_INVALID_PHONE, ex.errorCode)
    }

    @Test
    fun `should throw AUTH_OTP_COOLDOWN when OTP requested too soon`() {
        val recentOtp = OtpRequest(
            phoneNumber = validPhone,
            otpHash = "hash",
            expiresAt = Instant.now().plusSeconds(240),
            createdAt = Instant.now().minusSeconds(10) // 10s ago — within 60s cooldown
        )
        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns recentOtp

        val ex = assertThrows<BusinessException> {
            authService.requestOtp(validPhone)
        }
        assertEquals(ErrorCode.AUTH_OTP_COOLDOWN, ex.errorCode)
    }

    // ─── verifyOtp ──────────────────────────────────────

    @Test
    fun `should return AuthResult when OTP is correct and new user`() {
        val otpHash = passwordEncoder.encode(validOtp) ?: ""
        val activeOtp = OtpRequest(
            phoneNumber = validPhone,
            otpHash = otpHash,
            attempts = 0,
            expiresAt = Instant.now().plusSeconds(240),
            verified = false
        )

        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns activeOtp
        every { otpRepository.claimAttempt(activeOtp, any()) } returns true
        every { otpRepository.markVerified(activeOtp) } returns Unit
        every { userRepository.findByPhoneNumber(validPhone) } returns null
        every { userRepository.save(any()) } answers { firstArg() }
        every { phoneHashRepository.save(any(), any()) } returns Unit
        every { deviceRepository.findByUserIdAndPlatform(any(), any()) } returns null
        every { deviceRepository.findByUserId(any()) } returns emptyList()
        every { deviceRepository.save(any()) } answers { firstArg() }
        every { refreshTokenRepository.save(any()) } answers { firstArg() }

        val result = authService.verifyOtp(validPhone, validOtp, "Test Device", "android")

        assertNotNull(result.accessToken)
        assertNotNull(result.refreshToken)
        assertTrue(result.isNewUser)
        assertEquals(900L, result.expiresIn)
    }

    // ─── admin claim (#551) ─────────────────────────────
    //
    // The moderation review endpoints are guarded by requireAdmin(), which reads an "admin" claim
    // that nothing ever wrote. These two pin the chain from the users.is_admin column (V21) to a
    // token that actually carries it — at login and again at every refresh.

    private fun stubVerifiableOtp(): OtpRequest {
        val activeOtp = OtpRequest(
            phoneNumber = validPhone,
            otpHash = passwordEncoder.encode(validOtp) ?: "",
            attempts = 0,
            expiresAt = Instant.now().plusSeconds(240),
            verified = false
        )
        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns activeOtp
        every { otpRepository.claimAttempt(activeOtp, any()) } returns true
        every { otpRepository.markVerified(activeOtp) } returns Unit
        every { deviceRepository.findByUserIdAndPlatform(any(), any()) } returns null
        every { deviceRepository.findByUserId(any()) } returns emptyList()
        every { deviceRepository.save(any()) } answers { firstArg() }
        every { refreshTokenRepository.save(any()) } answers { firstArg() }
        return activeOtp
    }

    @Test
    fun `should mint an admin token when the user row has is_admin set`() {
        stubVerifiableOtp()
        val admin = User(
            id = UUID.randomUUID(),
            phoneNumber = validPhone,
            status = UserStatus.ACTIVE,
            isAdmin = true
        )
        every { userRepository.findByPhoneNumber(validPhone) } returns admin

        val result = authService.verifyOtp(validPhone, validOtp, "Test Device", "android")

        assertTrue(jwtProvider.validateToken(result.accessToken)?.isAdmin == true)
    }

    @Test
    fun `should record sign-in on an existing device without writing its row back when the device is known`() {
        // #908: the row was read before the write. Saving it back would rewrite a push_token another
        // device may have taken in between, so sign-in must use the targeted update only.
        stubVerifiableOtp()
        val user = User(id = UUID.randomUUID(), phoneNumber = validPhone, status = UserStatus.ACTIVE)
        val known = Device(userId = user.id, platform = "android", deviceName = "Old", pushToken = "stale-token")
        every { userRepository.findByPhoneNumber(validPhone) } returns user
        every { deviceRepository.findByUserIdAndPlatform(user.id, "android") } returns known
        every { deviceRepository.recordSignIn(known.id, "Test Device", any()) } returns 1

        val result = authService.verifyOtp(validPhone, validOtp, "Test Device", "android")

        assertEquals(known.id.toString(), result.deviceId)
        verify(exactly = 1) { deviceRepository.recordSignIn(known.id, "Test Device", any()) }
        verify(exactly = 0) { deviceRepository.save(any()) }
    }

    @Test
    fun `should mint an ordinary token when the user row does not have is_admin set`() {
        stubVerifiableOtp()
        val plain = User(id = UUID.randomUUID(), phoneNumber = validPhone, status = UserStatus.ACTIVE)
        every { userRepository.findByPhoneNumber(validPhone) } returns plain

        val result = authService.verifyOtp(validPhone, validOtp, "Test Device", "android")

        assertEquals(false, jwtProvider.validateToken(result.accessToken)?.isAdmin)
    }

    @Test
    fun `should re-read is_admin from the user row on refresh`() {
        // Carried over from the old token instead, a revoke would survive until the holder logged
        // out. Re-reading bounds it to one access-token lifetime.
        val userId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        val refreshToken = "some-refresh-token"
        every { refreshTokenRepository.findByTokenHash(any()) } returns RefreshTokenRecord(
            userId = userId,
            deviceId = deviceId,
            tokenHash = "hash",
            expiresAt = Instant.now().plusSeconds(3600)
        )
        every { refreshTokenRepository.revokeByTokenHash(any()) } returns Unit
        every { refreshTokenRepository.save(any()) } answers { firstArg() }
        every { userRepository.findById(userId) } returns User(
            id = userId,
            phoneNumber = validPhone,
            status = UserStatus.ACTIVE,
            isAdmin = true
        )

        val result = authService.refresh(refreshToken)

        assertTrue(jwtProvider.validateToken(result.accessToken)?.isAdmin == true)
        verify { userRepository.findById(userId) }
    }

    @Test
    fun `should throw AUTH_OTP_EXPIRED when no active OTP`() {
        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns null

        val ex = assertThrows<BusinessException> {
            authService.verifyOtp(validPhone, validOtp, "Device", "android")
        }
        assertEquals(ErrorCode.AUTH_OTP_EXPIRED, ex.errorCode)
    }

    @Test
    fun `should throw AUTH_OTP_INVALID when wrong OTP`() {
        val otpHash = passwordEncoder.encode("999999") ?: ""
        val activeOtp = OtpRequest(
            phoneNumber = validPhone,
            otpHash = otpHash,
            attempts = 0,
            expiresAt = Instant.now().plusSeconds(240)
        )

        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns activeOtp
        every { otpRepository.claimAttempt(activeOtp, any()) } returns true

        val ex = assertThrows<BusinessException> {
            authService.verifyOtp(validPhone, "123456", "Device", "android")
        }
        assertEquals(ErrorCode.AUTH_OTP_INVALID, ex.errorCode)
    }

    @Test
    fun `should throw AUTH_OTP_MAX_ATTEMPTS when too many attempts`() {
        val activeOtp = OtpRequest(
            phoneNumber = validPhone,
            otpHash = "hash",
            attempts = 5,
            expiresAt = Instant.now().plusSeconds(240)
        )

        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns activeOtp
        // The budget is spent, so the conditional UPDATE matches no row and claims nothing.
        every { otpRepository.claimAttempt(activeOtp, any()) } returns false

        val ex = assertThrows<BusinessException> {
            authService.verifyOtp(validPhone, validOtp, "Device", "android")
        }
        assertEquals(ErrorCode.AUTH_OTP_MAX_ATTEMPTS, ex.errorCode)
    }

    // ─── refresh ────────────────────────────────────────

    @Test
    fun `should return new tokens when refresh token is valid`() {
        val rawToken = jwtProvider.generateRefreshToken()
        val tokenHash = AuthService.sha256(rawToken)
        val userId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()

        val record = RefreshTokenRecord(
            userId = userId,
            deviceId = deviceId,
            tokenHash = tokenHash,
            expiresAt = Instant.now().plusSeconds(86400)
        )

        every { refreshTokenRepository.findByTokenHash(tokenHash) } returns record
        every { refreshTokenRepository.revokeByTokenHash(tokenHash) } returns Unit
        every { refreshTokenRepository.save(any()) } answers { firstArg() }
        // Refresh re-reads the user row for the admin flag (#551), so it needs a row to read.
        every { userRepository.findById(userId) } returns
            User(id = userId, phoneNumber = validPhone, status = UserStatus.ACTIVE)

        val result = authService.refresh(rawToken)

        assertNotNull(result.accessToken)
        assertNotNull(result.refreshToken)
        assertEquals(900L, result.expiresIn)
        // The rotated token belongs to the same user and device as the one it replaced; the
        // controller re-states both, and used to send empty strings for them.
        assertEquals(userId.toString(), result.userId)
        assertEquals(deviceId.toString(), result.deviceId)
        verify { refreshTokenRepository.revokeByTokenHash(tokenHash) }
    }

    @Test
    fun `should throw AUTH_TOKEN_INVALID when refresh token not found`() {
        every { refreshTokenRepository.findByTokenHash(any()) } returns null

        val ex = assertThrows<BusinessException> {
            authService.refresh("invalid-token")
        }
        assertEquals(ErrorCode.AUTH_TOKEN_INVALID, ex.errorCode)
    }

    // ─── logout (#908) ──────────────────────────────────

    @Test
    fun `logout should clear the device's push token as well as its refresh tokens`() {
        // A signed-out device that keeps its token is still a delivery address for this account.
        val device = Device(userId = UUID.randomUUID(), platform = "android", pushToken = "fcm-token")
        every { refreshTokenRepository.revokeAllForDevice(device.userId, device.id) } returns Unit
        every { deviceRepository.findById(device.id) } returns device
        val saved = slot<Device>()
        every { deviceRepository.save(capture(saved)) } answers { firstArg() }

        authService.logout(device.userId, device.id)

        verify { refreshTokenRepository.revokeAllForDevice(device.userId, device.id) }
        assertEquals(device.id, saved.captured.id)
        assertNull(saved.captured.pushToken)
    }

    @Test
    fun `logout should not touch a device that belongs to another account`() {
        val device = Device(userId = UUID.randomUUID(), platform = "android", pushToken = "fcm-token")
        val caller = UUID.randomUUID()
        every { refreshTokenRepository.revokeAllForDevice(caller, device.id) } returns Unit
        every { deviceRepository.findById(device.id) } returns device

        authService.logout(caller, device.id)

        verify(exactly = 0) { deviceRepository.save(any()) }
    }
}
