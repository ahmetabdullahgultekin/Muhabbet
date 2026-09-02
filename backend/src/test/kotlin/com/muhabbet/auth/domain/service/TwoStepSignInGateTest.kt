package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.FirebaseTokenVerifier
import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.InMemoryTwoStepAttemptRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant
import java.util.UUID

/**
 * The leg #566 was about: two-step verification is **asked for at sign-in**.
 *
 * Everything before this existed — the PIN was validated, hashed, stored and reported back by
 * `GET /status` — and `ErrorCode.AUTH_2FA_PIN_REQUIRED` was declared and thrown from nowhere. A user
 * could switch a second factor on and it changed nothing about signing in.
 *
 * These tests are deliberately about the *refusal*, not the round trip. A test that only proved the
 * PIN could be stored and read back would have passed against the broken code, which is precisely
 * how the defect survived being "done".
 *
 * Both token-minting paths are covered here, because gating one and not the other is worse than
 * gating neither — the feature would look enforced while the app's default sign-in walked past it.
 */
class TwoStepSignInGateTest {

    private lateinit var userRepository: UserRepository
    private lateinit var otpRepository: OtpRepository
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var phoneHashRepository: PhoneHashRepository
    private lateinit var otpSender: OtpSender
    private lateinit var attempts: InMemoryTwoStepAttemptRepository
    private lateinit var authService: AuthService

    private val phone = TestData.PHONE_1
    private val pin = "654321"
    private val encoder = BCryptPasswordEncoder()
    private val storedHash = encoder.encode(pin)

    /** A verifier that recognises exactly one token, so the Firebase path is reachable in a test. */
    private val firebaseVerifier = object : FirebaseTokenVerifier {
        override fun phoneNumberOf(idToken: String): String {
            // IllegalArgumentException is what the port documents for a token that does not verify.
            require(idToken == "good-token") { "bad token" }
            return phone
        }
    }

    private fun userWithTwoStep(enabled: Boolean) = User(
        id = TestData.USER_ID_1,
        phoneNumber = phone,
        status = UserStatus.ACTIVE,
        twoStepPinHash = if (enabled) storedHash else null,
        twoStepEnabledAt = if (enabled) Instant.now() else null
    )

    private val liveOtp = OtpRequest(
        phoneNumber = phone,
        otpHash = "irrelevant — the encoder below is asked, not this",
        expiresAt = Instant.now().plusSeconds(300)
    )

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        otpRepository = mockk(relaxed = true)
        deviceRepository = mockk()
        refreshTokenRepository = mockk(relaxed = true)
        phoneHashRepository = mockk(relaxed = true)
        otpSender = mockk(relaxed = true)
        attempts = InMemoryTwoStepAttemptRepository()

        val jwtProvider = JwtProvider(
            JwtProperties(
                secret = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256",
                accessTokenExpiry = 900,
                refreshTokenExpiry = 2592000,
                issuer = "muhabbet-test"
            ),
            MockEnvironment()
        )

        authService = AuthService(
            userRepository = userRepository,
            otpRepository = otpRepository,
            deviceRepository = deviceRepository,
            refreshTokenRepository = refreshTokenRepository,
            phoneHashRepository = phoneHashRepository,
            otpSender = otpSender,
            jwtProvider = jwtProvider,
            // The real encoder, because "does the stored hash match" is the thing under test.
            passwordEncoder = encoder,
            otpQuotaPort = mockk<OtpQuotaPort>().also { every { it.tryConsume(any()) } returns true },
            firebaseTokenVerifier = firebaseVerifier,
            twoStepAttemptRepository = attempts,
            twoStepMaxAttempts = 5,
            twoStepLockSeconds = 900
        )

        every { otpRepository.findActiveByPhoneNumber(phone) } returns liveOtp
        every { otpRepository.claimAttempt(any(), any()) } returns true
        // The OTP itself is always right in these tests: what is under examination is what happens
        // after it is right.
        every { deviceRepository.findByUserIdAndPlatform(any(), any()) } returns null
        every { deviceRepository.findByUserId(any()) } returns emptyList()
        every { deviceRepository.save(any()) } answers { firstArg() }
    }

    /** The service compares the *supplied* code against the stored hash, so store a real one. */
    private fun otpMatching(code: String) = liveOtp.copy(otpHash = encoder.encode(code) ?: "")

    private fun givenLiveOtpFor(code: String) {
        every { otpRepository.findActiveByPhoneNumber(phone) } returns otpMatching(code)
    }

    // ─── the refusal ────────────────────────────────────

    @Test
    fun `should refuse the OTP sign-in when two-step is on and no PIN is supplied`() {
        givenLiveOtpFor("111111")
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = true)

        val thrown = assertThrows(BusinessException::class.java) {
            authService.verifyOtp(phone, "111111", "Pixel", "android")
        }

        assertEquals(ErrorCode.AUTH_2FA_PIN_REQUIRED, thrown.errorCode)
        // The code was right, so the sign-in must be resumable with the same code plus a PIN.
        verify { otpRepository.refundAttempt(any()) }
        verify(exactly = 0) { otpRepository.markVerified(any()) }
    }

    @Test
    fun `should refuse the OTP sign-in when the supplied PIN is wrong`() {
        givenLiveOtpFor("111111")
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = true)

        val thrown = assertThrows(BusinessException::class.java) {
            authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = "000000")
        }

        assertEquals(ErrorCode.AUTH_2FA_PIN_INVALID, thrown.errorCode)
        verify(exactly = 0) { otpRepository.markVerified(any()) }
    }

    @Test
    fun `should refuse the Firebase sign-in when two-step is on and no PIN is supplied`() {
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = true)

        val thrown = assertThrows(BusinessException::class.java) {
            authService.verifyFirebaseToken("good-token", "Pixel", "android")
        }

        assertEquals(ErrorCode.AUTH_2FA_PIN_REQUIRED, thrown.errorCode)
    }

    @Test
    fun `should refuse the Firebase sign-in when the supplied PIN is wrong`() {
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = true)

        val thrown = assertThrows(BusinessException::class.java) {
            authService.verifyFirebaseToken("good-token", "Pixel", "android", twoStepPin = "000000")
        }

        assertEquals(ErrorCode.AUTH_2FA_PIN_INVALID, thrown.errorCode)
    }

    // ─── and the ways through ───────────────────────────

    @Test
    fun `should sign in when the correct PIN accompanies the code`() {
        givenLiveOtpFor("111111")
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = true)

        val result = authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = pin)

        assertNotNull(result.accessToken)
        verify { otpRepository.markVerified(any()) }
    }

    @Test
    fun `should sign in without a PIN when two-step is off`() {
        givenLiveOtpFor("111111")
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = false)

        val result = authService.verifyOtp(phone, "111111", "Pixel", "android")

        assertNotNull(result.accessToken)
        // Nothing was charged against a budget the account does not have.
        assertEquals(0, attempts.claims)
        verify(exactly = 0) { otpRepository.refundAttempt(any()) }
    }

    @Test
    fun `should sign a brand new user in without asking for a PIN`() {
        givenLiveOtpFor("111111")
        every { userRepository.findByPhoneNumber(phone) } returns null
        every { userRepository.save(any()) } answers { firstArg() }

        val result = authService.verifyOtp(phone, "111111", "Pixel", "android")

        assertNotNull(result.accessToken)
        assertEquals(0, attempts.claims)
    }

    // ─── the budget ─────────────────────────────────────

    @Test
    fun `should lock the PIN out after the configured number of wrong guesses`() {
        givenLiveOtpFor("111111")
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = true)

        // Five wrong guesses are merely wrong. Each spends one of the five.
        repeat(5) {
            val thrown = assertThrows(BusinessException::class.java) {
                authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = "000000")
            }
            assertEquals(ErrorCode.AUTH_2FA_PIN_INVALID, thrown.errorCode)
        }

        val sixth = assertThrows(BusinessException::class.java) {
            authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = "000000")
        }
        assertEquals(ErrorCode.AUTH_2FA_LOCKED, sixth.errorCode)
    }

    @Test
    fun `should refuse even the correct PIN while the account is locked out`() {
        givenLiveOtpFor("111111")
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = true)

        repeat(5) {
            assertThrows(BusinessException::class.java) {
                authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = "000000")
            }
        }

        // A lock that lets the right answer through is a message, not a control — an attacker who
        // eventually guesses correctly would be admitted regardless of how many tries it took.
        val thrown = assertThrows(BusinessException::class.java) {
            authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = pin)
        }
        assertEquals(ErrorCode.AUTH_2FA_LOCKED, thrown.errorCode)
    }

    @Test
    fun `should not spend a PIN attempt merely for being told one is needed`() {
        givenLiveOtpFor("111111")
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = true)

        // The client cannot know an account has a second factor until it asks. Charging it for
        // asking would start every sign-in one guess down.
        repeat(10) {
            assertThrows(BusinessException::class.java) {
                authService.verifyOtp(phone, "111111", "Pixel", "android")
            }
        }

        assertEquals(0, attempts.claims)
        val result = authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = pin)
        assertNotNull(result.accessToken)
    }

    @Test
    fun `should forget past failures once the right PIN arrives`() {
        givenLiveOtpFor("111111")
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = true)

        repeat(4) {
            assertThrows(BusinessException::class.java) {
                authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = "000000")
            }
        }
        authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = pin)

        // Four typos last March plus one today must not add up to a lockout.
        repeat(4) {
            val thrown = assertThrows(BusinessException::class.java) {
                authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = "000000")
            }
            assertEquals(ErrorCode.AUTH_2FA_PIN_INVALID, thrown.errorCode)
        }
    }

    @Test
    fun `should treat a half-written two-step row as off rather than as a permanent lockout`() {
        givenLiveOtpFor("111111")
        // Only one of the two columns set — a state no code path writes, but one a hand-run UPDATE
        // could. Failing closed here would lock the account out with no PIN that could ever open it.
        every { userRepository.findByPhoneNumber(phone) } returns User(
            id = UUID.randomUUID(),
            phoneNumber = phone,
            twoStepEnabledAt = Instant.now(),
            twoStepPinHash = null
        )

        val result = authService.verifyOtp(phone, "111111", "Pixel", "android")

        assertNotNull(result.accessToken)
    }

    @Test
    fun `should record the device on a two-step sign-in exactly once`() {
        givenLiveOtpFor("111111")
        every { userRepository.findByPhoneNumber(phone) } returns userWithTwoStep(enabled = true)

        assertThrows(BusinessException::class.java) {
            authService.verifyOtp(phone, "111111", "Pixel", "android")
        }
        authService.verifyOtp(phone, "111111", "Pixel", "android", twoStepPin = pin)

        // The refused attempt must not have created a device or a session on its way to the gate.
        verify(exactly = 1) { deviceRepository.save(any<Device>()) }
    }
}
