package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import java.time.Instant

/**
 * Covers the branch taken when an external verification provider owns the code, so that the
 * provider — not this service — decides whether a submitted code is correct.
 */
class AuthServiceVerifierTest {

    private val phone = "+905000000001"

    private lateinit var otpRepository: OtpRepository
    private lateinit var otpVerifier: OtpVerifier
    private lateinit var authService: AuthService

    @BeforeEach
    fun setUp() {
        otpRepository = mockk(relaxed = true)
        otpVerifier = mockk(relaxed = true)

        val env = MockEnvironment().withProperty("spring.profiles.active", "test")
        val jwtProvider = JwtProvider(
            JwtProperties(secret = "test-secret-that-is-long-enough-for-hs256-signing-1234567890"),
            env,
        )

        authService = AuthService(
            userRepository = mockk<UserRepository>(relaxed = true),
            otpRepository = otpRepository,
            deviceRepository = mockk<DeviceRepository>(relaxed = true),
            refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true),
            phoneHashRepository = mockk<PhoneHashRepository>(relaxed = true),
            // No sender: the constructor now rejects having both, which is the point.
            jwtProvider = jwtProvider,
            passwordEncoder = BCryptPasswordEncoder(),
            otpVerifier = otpVerifier,
        )
    }

    @Test
    fun `should ask the verifier to send and never use the local sender`() = runBlocking {
        coEvery { otpRepository.findActiveByPhoneNumber(phone) } returns null
        val saved = slot<OtpRequest>()
        coEvery { otpRepository.save(capture(saved)) } answers { saved.captured }

        val result = authService.requestOtp(phone)

        coVerify(exactly = 1) { otpVerifier.start(phone) }
        // No code exists on this side, so none can be echoed back even in mock mode.
        assertNull(result.mockCode)
        assertEquals(AuthService.EXTERNALLY_VERIFIED, saved.captured.otpHash)
    }

    @Test
    fun `should reject the code when the verifier rejects it`() = runBlocking {
        coEvery { otpRepository.findActiveByPhoneNumber(phone) } returns activeRequest()
        coEvery { otpVerifier.check(phone, "000000") } returns false

        val error = assertThrows<BusinessException> {
            runBlocking { authService.verifyOtp(phone, "000000", "Pixel", "ANDROID") }
        }

        assertEquals(ErrorCode.AUTH_OTP_INVALID, error.errorCode)
        coVerify(exactly = 1) { otpVerifier.check(phone, "000000") }
    }

    @Test
    fun `should still count a failed attempt against the local limit`() = runBlocking {
        val active = activeRequest()
        coEvery { otpRepository.findActiveByPhoneNumber(phone) } returns active
        coEvery { otpVerifier.check(any(), any()) } returns false

        assertThrows<BusinessException> {
            runBlocking { authService.verifyOtp(phone, "111111", "Pixel", "ANDROID") }
        }

        // Attempt limiting stays ours even though the code check is delegated.
        coVerify(exactly = 1) { otpRepository.incrementAttempts(active) }
    }

    @Test
    fun `should refuse to start with no delivery path at all`() {
        // muhabbet.sms.provider=twilio-verify matches no OtpSender, so without this guard the
        // context died on an unresolved OtpSender bean and took the whole API down with it.
        val error = assertThrows<IllegalArgumentException> { buildService(sender = null, verifier = null) }
        assertTrue(error.message!!.contains("muhabbet.sms.provider"))
    }

    @Test
    fun `should refuse to start with two competing delivery paths`() {
        val error = assertThrows<IllegalArgumentException> {
            buildService(sender = mockk(relaxed = true), verifier = mockk(relaxed = true))
        }
        assertTrue(error.message!!.contains("exactly one"))
    }

    private fun buildService(sender: OtpSender?, verifier: OtpVerifier?) = AuthService(
        userRepository = mockk(relaxed = true),
        otpRepository = mockk(relaxed = true),
        deviceRepository = mockk(relaxed = true),
        refreshTokenRepository = mockk(relaxed = true),
        phoneHashRepository = mockk(relaxed = true),
        otpSender = sender,
        jwtProvider = JwtProvider(
            JwtProperties(secret = "test-secret-that-is-long-enough-for-hs256-signing-1234567890"),
            MockEnvironment().withProperty("spring.profiles.active", "test"),
        ),
        passwordEncoder = BCryptPasswordEncoder(),
        otpVerifier = verifier,
    )

    private fun activeRequest() = OtpRequest(
        phoneNumber = phone,
        otpHash = AuthService.EXTERNALLY_VERIFIED,
        expiresAt = Instant.now().plusSeconds(300),
    )
}
