package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.DevicePlatform
import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.OtpChannel
import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.AuthTestFixtures
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtProvider
import com.muhabbet.shared.transaction.TransactionRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
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
    private lateinit var otpQuota: OtpQuotaPort
    private lateinit var jwtProvider: JwtProvider
    private lateinit var passwordEncoder: BCryptPasswordEncoder
    private lateinit var transactions: RecordingTransactionRunner
    private lateinit var authService: AuthService

    private val validPhone = "+905321234567"
    private val validOtp = "123456"

    /** Runs the block and remembers whether one is running, so a test can ask "inside a transaction?". */
    private class RecordingTransactionRunner : TransactionRunner {
        var active = false
            private set
        var blocksRun = 0
            private set

        override fun <T : Any> inTransaction(block: () -> T): T {
            active = true
            blocksRun++
            try {
                return block()
            } finally {
                active = false
            }
        }
    }

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        otpRepository = mockk()
        deviceRepository = mockk()
        refreshTokenRepository = mockk()
        phoneHashRepository = mockk()
        otpSender = mockk()
        otpQuota = AuthTestFixtures.allowingQuota()
        passwordEncoder = BCryptPasswordEncoder()
        jwtProvider = AuthTestFixtures.jwtProvider()
        transactions = RecordingTransactionRunner()

        authService = AuthTestFixtures.authService(
            otpChannel = OtpChannel.LocalCode(otpSender),
            userRepository = userRepository,
            otpRepository = otpRepository,
            deviceRepository = deviceRepository,
            refreshTokenRepository = refreshTokenRepository,
            phoneHashRepository = phoneHashRepository,
            passwordEncoder = passwordEncoder,
            jwtProvider = jwtProvider,
            transactions = transactions,
            otpQuotaPort = otpQuota
        )
    }

    // ─── requestOtp ─────────────────────────────────────

    @Test
    fun `should return OtpResult when valid phone and no cooldown`() {
        every { otpRepository.createUnlessCoolingDown(any(), any()) } answers { firstArg() }
        every { otpSender.send(validPhone, any()) } returns Unit

        val result = authService.requestOtp(validPhone)

        assertEquals(300, result.ttlSeconds)
        assertEquals(60, result.retryAfterSeconds)
        verify { otpRepository.createUnlessCoolingDown(any(), any()) }
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
    fun `should throw AUTH_OTP_COOLDOWN and send nothing when the number is cooling down`() {
        every { otpRepository.createUnlessCoolingDown(any(), any()) } returns null

        val ex = assertThrows<BusinessException> { authService.requestOtp(validPhone) }

        assertEquals(ErrorCode.AUTH_OTP_COOLDOWN, ex.errorCode)
        verify(exactly = 0) { otpSender.send(any(), any()) }
        verify(exactly = 0) { otpQuota.tryConsume(any()) }
    }

    @Test
    fun `should call the SMS provider outside any transaction`() {
        // #886: a provider that takes seconds used to pin a pool connection for all of it.
        every { otpRepository.createUnlessCoolingDown(any(), any()) } answers { firstArg() }
        var sentInsideTransaction: Boolean? = null
        every { otpSender.send(validPhone, any()) } answers { sentInsideTransaction = transactions.active }

        authService.requestOtp(validPhone)

        assertEquals(false, sentInsideTransaction)
        assertEquals(1, transactions.blocksRun, "the cooldown decision and the insert are one transaction")
    }

    @Test
    fun `should retire the request and refund the budget when the provider fails`() {
        val stored = slot<OtpRequest>()
        every { otpRepository.createUnlessCoolingDown(capture(stored), any()) } answers { stored.captured }
        every { otpRepository.retire(any()) } returns Unit
        val outage = IllegalStateException("provider down")
        every { otpSender.send(validPhone, any()) } throws outage

        val thrown = assertThrows<IllegalStateException> { authService.requestOtp(validPhone) }

        assertSame(outage, thrown)
        verify { otpRepository.retire(stored.captured) }
        verify { otpQuota.refund(validPhone) }
    }

    @Test
    fun `should retire the undelivered request when the budget refuses it`() {
        every { otpRepository.createUnlessCoolingDown(any(), any()) } answers { firstArg() }
        every { otpRepository.retire(any()) } returns Unit
        every { otpQuota.tryConsume(validPhone) } returns false

        val ex = assertThrows<BusinessException> { authService.requestOtp(validPhone) }

        assertEquals(ErrorCode.AUTH_OTP_RATE_LIMIT, ex.errorCode)
        verify { otpRepository.retire(any()) }
        verify(exactly = 0) { otpSender.send(any(), any()) }
        verify(exactly = 0) { otpQuota.refund(any()) }
    }

    // ─── verifyOtp ──────────────────────────────────────

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
        every { otpRepository.markVerified(activeOtp) } returns true
        every { deviceRepository.findActiveByInstallation(any(), any()) } returns null
        every { deviceRepository.findLegacyPrimaryCandidate(any(), any()) } returns null
        every { deviceRepository.findActiveByUserId(any()) } returns emptyList()
        every { deviceRepository.save(any()) } answers { firstArg() }
        every { refreshTokenRepository.save(any()) } answers { firstArg() }
        return activeOtp
    }

    @Test
    fun `should return AuthResult when OTP is correct and new user`() {
        stubVerifiableOtp()
        every { userRepository.findByPhoneNumber(validPhone) } returns null
        every { userRepository.save(any()) } answers { firstArg() }
        every { phoneHashRepository.save(any(), any()) } returns Unit

        val result = authService.verifyOtp(validPhone, validOtp, "Test Device", "android")

        assertNotNull(result.accessToken)
        assertNotNull(result.refreshToken)
        assertTrue(result.isNewUser)
        assertEquals(900L, result.expiresIn)
    }

    @Test
    fun `should stamp the device's token epoch into the access token`() {
        // #923: the epoch is what lets a logout or a revoke kill this token before it expires.
        stubVerifiableOtp()
        val user = User(id = UUID.randomUUID(), phoneNumber = validPhone, status = UserStatus.ACTIVE)
        every { userRepository.findByPhoneNumber(validPhone) } returns user
        val installation = UUID.randomUUID()
        every { deviceRepository.findActiveByInstallation(user.id, installation) } returns
            Device(userId = user.id, platform = DevicePlatform.ANDROID, installationId = installation, tokenVersion = 7)

        val result = authService.verifyOtp(validPhone, validOtp, "Pixel", "android", installationId = installation.toString())

        assertEquals(7, jwtProvider.validateToken(result.accessToken)?.tokenVersion)
    }

    @Test
    fun `should not carry an admin flag in the token even for an admin`() {
        // Privilege is read from the database per request (#923); a flag in the token would
        // outlive a demotion by the token's whole lifetime.
        stubVerifiableOtp()
        every { userRepository.findByPhoneNumber(validPhone) } returns
            User(id = UUID.randomUUID(), phoneNumber = validPhone, status = UserStatus.ACTIVE, isAdmin = true)

        val result = authService.verifyOtp(validPhone, validOtp, "Test Device", "android")

        assertEquals(false, jwtProvider.validateToken(result.accessToken)?.isAdmin)
    }

    @Test
    fun `should refuse a suspended account instead of issuing tokens no request will accept`() {
        stubVerifiableOtp()
        every { userRepository.findByPhoneNumber(validPhone) } returns
            User(id = UUID.randomUUID(), phoneNumber = validPhone, status = UserStatus.SUSPENDED)

        val ex = assertThrows<BusinessException> {
            authService.verifyOtp(validPhone, validOtp, "Test Device", "android")
        }

        assertEquals(ErrorCode.AUTH_ACCOUNT_SUSPENDED, ex.errorCode)
        verify(exactly = 0) { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `should reject an unknown platform with VALIDATION_ERROR before spending an attempt`() {
        stubVerifiableOtp()

        val ex = assertThrows<BusinessException> {
            authService.verifyOtp(validPhone, validOtp, "Toaster", "toaster-os")
        }

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
        verify(exactly = 0) { otpRepository.claimAttempt(any(), any()) }
    }

    @Test
    fun `should reject a malformed installation id with VALIDATION_ERROR`() {
        stubVerifiableOtp()

        val ex = assertThrows<BusinessException> {
            authService.verifyOtp(validPhone, validOtp, "Pixel", "android", installationId = "not-a-uuid")
        }

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
    }

    @Test
    fun `should refuse the second of two verifies of one code`() {
        // The consume is conditional; the loser of two concurrent verifies finds it already spent.
        val otp = stubVerifiableOtp()
        every { otpRepository.markVerified(otp) } returns false
        every { userRepository.findByPhoneNumber(validPhone) } returns null

        val ex = assertThrows<BusinessException> {
            authService.verifyOtp(validPhone, validOtp, "Test Device", "android")
        }

        assertEquals(ErrorCode.AUTH_OTP_EXPIRED, ex.errorCode)
        verify(exactly = 0) { refreshTokenRepository.save(any()) }
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
        assertFalse(transactions.blocksRun > 0, "a wrong code opened a transaction it had no use for")
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

    // ─── registerPushToken ──────────────────────────────
    //
    // The language half of #469. Push text is composed on the server, so the device row is the only
    // place the reader's language can come from; before V22 there was no column and every
    // notification went out in Turkish.

    private fun registerPushToken(existing: Device, locale: String?): Device {
        every { deviceRepository.findByUserId(existing.userId) } returns listOf(existing)
        val saved = slot<Device>()
        every { deviceRepository.save(capture(saved)) } answers { firstArg() }

        authService.registerPushToken(existing.userId, existing.id, "fcm-token", locale)

        return saved.captured
    }

    private fun device(locale: String? = null) = Device(
        userId = UUID.randomUUID(),
        platform = DevicePlatform.ANDROID,
        locale = locale
    )

    @Test
    fun `should store the language the device registered with its push token`() {
        val saved = registerPushToken(device(), locale = "en")

        assertEquals("en", saved.locale)
        assertEquals("fcm-token", saved.pushToken)
    }

    @Test
    fun `should normalise the language before storing it`() {
        val saved = registerPushToken(device(), locale = "  EN-gb ")

        assertEquals("en-GB", saved.locale)
    }

    @Test
    fun `should keep the previous language when the caller sends none`() {
        // onNewToken fires in a system callback with no UI and nothing to report. Clearing the
        // language every time Firebase rotates a token would put the device back on the fallback.
        val saved = registerPushToken(device(locale = "en"), locale = null)

        assertEquals("en", saved.locale)
    }

    @Test
    fun `should keep the previous language when the caller sends nonsense`() {
        val saved = registerPushToken(device(locale = "en"), locale = "!!!")

        assertEquals("en", saved.locale)
    }

    @Test
    fun `should leave the language unknown when nothing has ever been registered`() {
        val saved = registerPushToken(device(locale = null), locale = null)

        assertNull(saved.locale)
    }

    @Test
    fun `should reject a push token for a device that is not the callers`() {
        val owner = UUID.randomUUID()
        every { deviceRepository.findByUserId(owner) } returns emptyList()

        val ex = assertThrows<BusinessException> {
            authService.registerPushToken(owner, UUID.randomUUID(), "fcm-token", "en")
        }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.errorCode)
    }
}
