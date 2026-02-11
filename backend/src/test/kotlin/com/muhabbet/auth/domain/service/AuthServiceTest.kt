package com.muhabbet.auth.domain.service

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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
        jwtProvider = JwtProvider(jwtProperties)

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
        )
    }

    // ─── requestOtp ─────────────────────────────────────

    @Test
    fun `should return OtpResult when valid phone and no cooldown`() = runBlocking {
        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns null
        every { otpRepository.save(any()) } answers { firstArg() }
        coEvery { otpSender.send(validPhone, any()) } returns Unit

        val result = authService.requestOtp(validPhone)

        assertEquals(300, result.ttlSeconds)
        assertEquals(60, result.retryAfterSeconds)
        verify { otpRepository.save(any()) }
        coVerify { otpSender.send(validPhone, any()) }
    }

    @Test
    fun `should throw AUTH_INVALID_PHONE when phone is invalid`() {
        val ex = assertThrows<BusinessException> {
            runBlocking { authService.requestOtp("12345") }
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
            runBlocking { authService.requestOtp(validPhone) }
        }
        assertEquals(ErrorCode.AUTH_OTP_COOLDOWN, ex.errorCode)
    }

    // ─── verifyOtp ──────────────────────────────────────

    @Test
    fun `should return AuthResult when OTP is correct and new user`() = runBlocking {
        val otpHash = passwordEncoder.encode(validOtp)
        val activeOtp = OtpRequest(
            phoneNumber = validPhone,
            otpHash = otpHash,
            attempts = 0,
            expiresAt = Instant.now().plusSeconds(240),
            verified = false
        )

        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns activeOtp
        every { otpRepository.incrementAttempts(activeOtp) } returns Unit
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

    @Test
    fun `should throw AUTH_OTP_EXPIRED when no active OTP`() {
        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns null

        val ex = assertThrows<BusinessException> {
            runBlocking { authService.verifyOtp(validPhone, validOtp, "Device", "android") }
        }
        assertEquals(ErrorCode.AUTH_OTP_EXPIRED, ex.errorCode)
    }

    @Test
    fun `should throw AUTH_OTP_INVALID when wrong OTP`() {
        val otpHash = passwordEncoder.encode("999999")
        val activeOtp = OtpRequest(
            phoneNumber = validPhone,
            otpHash = otpHash,
            attempts = 0,
            expiresAt = Instant.now().plusSeconds(240)
        )

        every { otpRepository.findActiveByPhoneNumber(validPhone) } returns activeOtp
        every { otpRepository.incrementAttempts(activeOtp) } returns Unit

        val ex = assertThrows<BusinessException> {
            runBlocking { authService.verifyOtp(validPhone, "123456", "Device", "android") }
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

        val ex = assertThrows<BusinessException> {
            runBlocking { authService.verifyOtp(validPhone, validOtp, "Device", "android") }
        }
        assertEquals(ErrorCode.AUTH_OTP_MAX_ATTEMPTS, ex.errorCode)
    }

    // ─── refresh ────────────────────────────────────────

    @Test
    fun `should return new tokens when refresh token is valid`() = runBlocking {
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

        val result = authService.refresh(rawToken)

        assertNotNull(result.accessToken)
        assertNotNull(result.refreshToken)
        assertEquals(900L, result.expiresIn)
        verify { refreshTokenRepository.revokeByTokenHash(tokenHash) }
    }

    @Test
    fun `should throw AUTH_TOKEN_INVALID when refresh token not found`() {
        every { refreshTokenRepository.findByTokenHash(any()) } returns null

        val ex = assertThrows<BusinessException> {
            runBlocking { authService.refresh("invalid-token") }
        }
        assertEquals(ErrorCode.AUTH_TOKEN_INVALID, ex.errorCode)
    }
}
