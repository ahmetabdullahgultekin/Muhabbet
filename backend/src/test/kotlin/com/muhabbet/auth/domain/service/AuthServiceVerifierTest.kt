package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.OtpRequest
import com.muhabbet.auth.domain.port.out.OtpChannel
import com.muhabbet.auth.domain.port.out.OtpRepository
import com.muhabbet.auth.domain.port.out.OtpSender
import com.muhabbet.auth.domain.port.out.OtpVerifier
import com.muhabbet.shared.AuthTestFixtures
import com.muhabbet.shared.config.AuthSessionConfig
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.transaction.TransactionRunner
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

        // claimAttempt decides whether the request is allowed to proceed at all, and a relaxed mock
        // answers false — which would short-circuit every test here into AUTH_OTP_MAX_ATTEMPTS before
        // the verifier is ever consulted. Grant by default; the limit itself is covered elsewhere.
        every { otpRepository.claimAttempt(any(), any()) } returns true

        authService = AuthTestFixtures.authService(
            otpChannel = OtpChannel.ExternalVerifier(otpVerifier),
            otpRepository = otpRepository
        )
    }

    @Test
    fun `should ask the verifier to send and never use the local sender`() {
        every { otpRepository.findActiveByPhoneNumber(phone) } returns null
        val saved = slot<OtpRequest>()
        every { otpRepository.createUnlessCoolingDown(capture(saved), any()) } answers { saved.captured }

        val result = authService.requestOtp(phone)

        verify(exactly = 1) { otpVerifier.start(phone) }
        // No code exists on this side, so none can be echoed back even in mock mode.
        assertNull(result.mockCode)
        assertEquals(AuthService.EXTERNALLY_VERIFIED, saved.captured.otpHash)
    }

    @Test
    fun `should reject the code when the verifier rejects it`() {
        every { otpRepository.findActiveByPhoneNumber(phone) } returns activeRequest()
        every { otpVerifier.check(phone, "000000") } returns false

        val error = assertThrows<BusinessException> {
            authService.verifyOtp(phone, "000000", "Pixel", "ANDROID")
        }

        assertEquals(ErrorCode.AUTH_OTP_INVALID, error.errorCode)
        verify(exactly = 1) { otpVerifier.check(phone, "000000") }
    }

    @Test
    fun `should still count a failed attempt against the local limit`() {
        val active = activeRequest()
        every { otpRepository.findActiveByPhoneNumber(phone) } returns active
        every { otpVerifier.check(any(), any()) } returns false

        assertThrows<BusinessException> {
            authService.verifyOtp(phone, "111111", "Pixel", "ANDROID")
        }

        // Attempt limiting stays ours even though the code check is delegated.
        verify(exactly = 1) { otpRepository.claimAttempt(active, any()) }
    }

    @Test
    fun `should check the code with the verifier outside any transaction`() {
        // #886: verifyOtp used to hold its own connection plus claimAttempt's REQUIRES_NEW one for
        // the whole Twilio round trip.
        var openTransactions = 0
        var checkedInsideTransaction: Boolean? = null
        val transactions = object : TransactionRunner {
            override fun <T : Any> inTransaction(block: () -> T): T {
                openTransactions++
                try { return block() } finally { openTransactions-- }
            }
        }
        every { otpRepository.findActiveByPhoneNumber(phone) } returns activeRequest()
        every { otpVerifier.check(phone, "123456") } answers {
            checkedInsideTransaction = openTransactions > 0
            false
        }
        val service = AuthTestFixtures.authService(
            otpChannel = OtpChannel.ExternalVerifier(otpVerifier),
            otpRepository = otpRepository,
            transactions = transactions
        )

        assertThrows<BusinessException> { service.verifyOtp(phone, "123456", "Pixel", "android") }

        assertEquals(false, checkedInsideTransaction)
    }

    @Test
    fun `should refuse to start with no delivery path at all`() {
        // muhabbet.sms.provider=twilio-verify matches no OtpSender, so without this guard the
        // context died on an unresolved OtpSender bean and took the whole API down with it.
        val error = assertThrows<IllegalStateException> { AuthSessionConfig().otpChannel(null, null) }
        assertTrue(error.message.orEmpty().contains("muhabbet.sms.provider"))
    }

    @Test
    fun `should refuse to start with two competing delivery paths`() {
        val error = assertThrows<IllegalStateException> {
            AuthSessionConfig().otpChannel(mockk<OtpSender>(relaxed = true), mockk<OtpVerifier>(relaxed = true))
        }
        assertTrue(error.message.orEmpty().contains("exactly one"))
    }

    private fun activeRequest() = OtpRequest(
        phoneNumber = phone,
        otpHash = AuthService.EXTERNALLY_VERIFIED,
        expiresAt = Instant.now().plusSeconds(300),
    )
}
