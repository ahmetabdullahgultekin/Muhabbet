package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.RefreshTokenRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.security.JwtProperties
import com.muhabbet.shared.security.JwtProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.env.MockEnvironment
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

/**
 * Covers the SMS bypass that makes emulator sign-in possible at all.
 *
 * Production uses Twilio Verify, so the code never exists on our side and a number nobody owns
 * receives nothing. Without a bypass, no test account can sign in, and every mobile change ships
 * compiled-but-never-seen — which is how one release went out with a crash on swipe-to-reply and
 * another with a crash on the camera button.
 *
 * The tests that matter here are the guards. A bypass is a door, and these assert the door is
 * narrow: unallocated numbers only, a hard cap, and no effect at all when unconfigured.
 */
class AuthServiceTestNumberTest {

    private val testPhone = "+905000000001"
    private val realPhone = "+905321234567"

    private fun buildService(
        testNumbers: Set<String>,
        otpRepository: OtpRepository = mockk(relaxed = true),
        otpVerifier: OtpVerifier = mockk(relaxed = true),
    ) = AuthService(
        userRepository = mockk<UserRepository>(relaxed = true),
        otpRepository = otpRepository,
        deviceRepository = mockk<DeviceRepository>(relaxed = true),
        refreshTokenRepository = mockk<RefreshTokenRepository>(relaxed = true),
        phoneHashRepository = mockk<PhoneHashRepository>(relaxed = true),
        jwtProvider = JwtProvider(
            JwtProperties(secret = "test-secret-that-is-long-enough-for-hs256-signing-1234567890"),
            MockEnvironment().withProperty("spring.profiles.active", "test"),
        ),
        passwordEncoder = BCryptPasswordEncoder(),
        otpVerifier = otpVerifier,
        testNumbers = testNumbers,
    )

    @Test
    fun `a test number is never handed to the SMS provider`() {
        val otpRepository = mockk<OtpRepository>(relaxed = true)
        val verifier = mockk<OtpVerifier>(relaxed = true)
        every { otpRepository.findActiveByPhoneNumber(testPhone) } returns null
        val saved = slot<OtpRequest>()
        every { otpRepository.save(capture(saved)) } answers { saved.captured }

        buildService(setOf(testPhone), otpRepository, verifier).requestOtp(testPhone)

        verify(exactly = 0) { verifier.start(any()) }
        // A real hash, not the sentinel — the code exists on this side and can be checked here.
        assertNotEquals(AuthService.EXTERNALLY_VERIFIED, saved.captured.otpHash)
        assertTrue(saved.captured.otpHash.startsWith("\$2"), "expected a bcrypt hash")
    }

    @Test
    fun `an ordinary number still goes to the verifier when test numbers are configured`() {
        // The bypass must be scoped to the list, not switched on globally by its presence.
        val otpRepository = mockk<OtpRepository>(relaxed = true)
        val verifier = mockk<OtpVerifier>(relaxed = true)
        every { otpRepository.findActiveByPhoneNumber(realPhone) } returns null
        val saved = slot<OtpRequest>()
        every { otpRepository.save(capture(saved)) } answers { saved.captured }

        buildService(setOf(testPhone), otpRepository, verifier).requestOtp(realPhone)

        verify(exactly = 1) { verifier.start(realPhone) }
        assertEquals(AuthService.EXTERNALLY_VERIFIED, saved.captured.otpHash)
    }

    @Test
    fun `a number outside the unallocated range is refused at startup`() {
        // The whole safety argument rests on +90500 being unassignable. If a real subscriber's
        // number could be listed, anyone who could edit the config could read their code from the
        // log and sign in as them.
        val error = assertThrows<IllegalArgumentException> { buildService(setOf(realPhone)) }
        assertTrue(error.message!!.contains("+90500"), error.message!!)
    }

    @Test
    fun `a malformed number is refused at startup`() {
        val error = assertThrows<IllegalArgumentException> { buildService(setOf("+90500")) }
        assertTrue(error.message!!.contains("malformed"), error.message!!)
    }

    @Test
    fun `more than a handful of test numbers is refused at startup`() {
        val tooMany = (1..AuthService.MAX_TEST_NUMBERS + 1)
            .map { "+905000000" + it.toString().padStart(3, '0') }
            .toSet()

        val error = assertThrows<IllegalArgumentException> { buildService(tooMany) }
        assertTrue(error.message!!.contains("bypass"), error.message!!)
    }

    @Test
    fun `with no test numbers configured the service behaves exactly as before`() {
        val otpRepository = mockk<OtpRepository>(relaxed = true)
        val verifier = mockk<OtpVerifier>(relaxed = true)
        every { otpRepository.findActiveByPhoneNumber(testPhone) } returns null
        val saved = slot<OtpRequest>()
        every { otpRepository.save(capture(saved)) } answers { saved.captured }

        // Same number as the first test; the only difference is that it is not configured.
        buildService(emptySet(), otpRepository, verifier).requestOtp(testPhone)

        verify(exactly = 1) { verifier.start(testPhone) }
        assertEquals(AuthService.EXTERNALLY_VERIFIED, saved.captured.otpHash)
    }
}
