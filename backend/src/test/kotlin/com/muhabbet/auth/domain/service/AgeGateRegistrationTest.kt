package com.muhabbet.auth.domain.service

import com.muhabbet.auth.adapter.out.external.SelfDeclarationAssurance
import com.muhabbet.auth.domain.model.AgeAssuranceLevel
import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.FirebaseTokenVerifier
import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import com.muhabbet.auth.domain.port.out.OtpRepository
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
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The age gate at registration (#751, ADR 0029 rule 1), on **both** token-minting paths.
 *
 * A gate on the OTP path alone would be the #566 mistake again: the app's default sign-in is the
 * Firebase one, so an OTP-only gate would look enforced while most registrations walked past it.
 *
 * The refusals assert that nothing was created — no user, no phone hash, no device, no token —
 * because a refused registration that still left an account behind would be no gate at all.
 */
class AgeGateRegistrationTest {

    private lateinit var userRepository: UserRepository
    private lateinit var otpRepository: OtpRepository
    private lateinit var deviceRepository: DeviceRepository
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var phoneHashRepository: PhoneHashRepository
    private lateinit var authService: AuthService

    private val phone = TestData.PHONE_1
    private val code = "111111"
    private val encoder = BCryptPasswordEncoder()

    /** Pinned to midday in Istanbul, so "fifteen today" is a fixed date rather than whenever CI runs. */
    private val today = LocalDate.of(2026, 11, 12)
    private val clock = Clock.fixed(Instant.parse("2026-11-12T09:00:00Z"), ZoneId.of("Europe/Istanbul"))

    private val fifteenToday = today.minusYears(15)
    private val fifteenTomorrow = fifteenToday.plusDays(1)

    private val firebaseVerifier = object : FirebaseTokenVerifier {
        override fun phoneNumberOf(idToken: String): String {
            require(idToken == "good-token") { "bad token" }
            return phone
        }
    }

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        otpRepository = mockk(relaxed = true)
        deviceRepository = mockk()
        refreshTokenRepository = mockk(relaxed = true)
        phoneHashRepository = mockk(relaxed = true)

        authService = AuthService(
            userRepository = userRepository,
            otpRepository = otpRepository,
            deviceRepository = deviceRepository,
            refreshTokenRepository = refreshTokenRepository,
            phoneHashRepository = phoneHashRepository,
            otpSender = mockk(relaxed = true),
            jwtProvider = JwtProvider(
                JwtProperties(
                    secret = "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256",
                    accessTokenExpiry = 900,
                    refreshTokenExpiry = 2592000,
                    issuer = "muhabbet-test"
                ),
                MockEnvironment()
            ),
            passwordEncoder = encoder,
            otpQuotaPort = mockk<OtpQuotaPort>().also { every { it.tryConsume(any()) } returns true },
            firebaseTokenVerifier = firebaseVerifier,
            twoStepAttemptRepository = InMemoryTwoStepAttemptRepository(),
            ageAssurancePort = SelfDeclarationAssurance(clock)
        )

        every { otpRepository.findActiveByPhoneNumber(phone) } returns OtpRequest(
            phoneNumber = phone,
            otpHash = encoder.encode(code) ?: "",
            expiresAt = Instant.now().plusSeconds(300)
        )
        every { otpRepository.claimAttempt(any(), any()) } returns true
        every { userRepository.save(any()) } answers { firstArg() }
        every { deviceRepository.findByUserIdAndPlatform(any(), any()) } returns null
        every { deviceRepository.findByUserId(any()) } returns emptyList()
        every { deviceRepository.save(any()) } answers { firstArg() }
    }

    private fun givenNoAccount() {
        every { userRepository.findByPhoneNumber(phone) } returns null
    }

    private fun assertNothingCreated() {
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { phoneHashRepository.save(any(), any()) }
        verify(exactly = 0) { deviceRepository.save(any()) }
        verify(exactly = 0) { refreshTokenRepository.save(any()) }
    }

    // ─── under fifteen is refused, on both paths ────────

    @Test
    fun `should refuse OTP registration when declared age is one day short of fifteen`() {
        givenNoAccount()

        val thrown = assertThrows(BusinessException::class.java) {
            authService.verifyOtp(phone, code, "Pixel", "android", birthDate = fifteenTomorrow)
        }

        assertEquals(ErrorCode.AUTH_AGE_BELOW_MINIMUM, thrown.errorCode)
        assertNothingCreated()
        verify(exactly = 0) { otpRepository.markVerified(any()) }
    }

    @Test
    fun `should refuse Firebase registration when declared age is below fifteen`() {
        givenNoAccount()

        val thrown = assertThrows(BusinessException::class.java) {
            authService.verifyFirebaseToken("good-token", "Pixel", "android", birthDate = today.minusYears(12))
        }

        assertEquals(ErrorCode.AUTH_AGE_BELOW_MINIMUM, thrown.errorCode)
        assertNothingCreated()
    }

    // ─── a new account must declare a date at all ───────

    @Test
    fun `should refuse OTP registration and refund the attempt when no birth date is supplied`() {
        givenNoAccount()

        val thrown = assertThrows(BusinessException::class.java) {
            authService.verifyOtp(phone, code, "Pixel", "android")
        }

        assertEquals(ErrorCode.AUTH_AGE_REQUIRED, thrown.errorCode)
        assertNothingCreated()
        // The client did not know a date was needed; the same code must work when it resends.
        verify(exactly = 1) { otpRepository.refundAttempt(any()) }
    }

    @Test
    fun `should refuse Firebase registration when no birth date is supplied`() {
        givenNoAccount()

        val thrown = assertThrows(BusinessException::class.java) {
            authService.verifyFirebaseToken("good-token", "Pixel", "android")
        }

        assertEquals(ErrorCode.AUTH_AGE_REQUIRED, thrown.errorCode)
        assertNothingCreated()
    }

    @Test
    fun `should refuse registration as invalid when declared birth date is in the future`() {
        givenNoAccount()

        val thrown = assertThrows(BusinessException::class.java) {
            authService.verifyOtp(phone, code, "Pixel", "android", birthDate = today.plusDays(1))
        }

        assertEquals(ErrorCode.AUTH_BIRTH_DATE_INVALID, thrown.errorCode)
        assertNothingCreated()
    }

    // ─── fifteen and older is accepted ──────────────────

    @Test
    fun `should create the account with a self-declared assurance when OTP registrant turns fifteen today`() {
        givenNoAccount()
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { firstArg() }

        val result = authService.verifyOtp(phone, code, "Pixel", "android", birthDate = fifteenToday)

        assertTrue(result.isNewUser)
        assertFalse(result.ageVerificationRequired)
        assertEquals(fifteenToday, saved.captured.birthDate)
        assertEquals(AgeAssuranceLevel.SELF_DECLARED, saved.captured.ageAssuranceLevel)
        assertNotNull(saved.captured.ageAssuredAt)
    }

    @Test
    fun `should create the account when Firebase registrant is an adult`() {
        givenNoAccount()
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { firstArg() }

        val result = authService.verifyFirebaseToken(
            "good-token", "Pixel", "android", birthDate = LocalDate.of(1990, 5, 1)
        )

        assertTrue(result.isNewUser)
        assertFalse(result.ageVerificationRequired)
        assertEquals(AgeAssuranceLevel.SELF_DECLARED, saved.captured.ageAssuranceLevel)
    }

    // ─── existing accounts are flagged, not locked out ──

    @Test
    fun `should sign in and flag age verification when existing account has no birth date`() {
        every { userRepository.findByPhoneNumber(phone) } returns User(id = TestData.USER_ID_1, phoneNumber = phone)

        val otpResult = authService.verifyOtp(phone, code, "Pixel", "android")
        val firebaseResult = authService.verifyFirebaseToken("good-token", "Pixel", "android")

        assertFalse(otpResult.isNewUser)
        assertTrue(otpResult.ageVerificationRequired)
        assertTrue(firebaseResult.ageVerificationRequired)
        // Signing in an existing account never writes the user row.
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `should ignore a supplied birth date when the account already exists`() {
        every { userRepository.findByPhoneNumber(phone) } returns User(id = TestData.USER_ID_1, phoneNumber = phone)

        val result = authService.verifyOtp(phone, code, "Pixel", "android", birthDate = today.minusYears(10))

        // Existing accounts declare through PATCH /users/me/birth-date, once; sign-in is not a back door.
        assertTrue(result.ageVerificationRequired)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `should not flag age verification when existing account has an assured age`() {
        every { userRepository.findByPhoneNumber(phone) } returns User(
            id = TestData.USER_ID_1,
            phoneNumber = phone,
            birthDate = LocalDate.of(1990, 1, 1),
            ageAssuranceLevel = AgeAssuranceLevel.SELF_DECLARED,
            ageAssuredAt = Instant.now()
        )

        val result = authService.verifyOtp(phone, code, "Pixel", "android")

        assertFalse(result.ageVerificationRequired)
    }
}
