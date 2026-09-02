package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.InMemoryTwoStepAttemptRepository
import com.muhabbet.shared.TestData
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
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant

/**
 * The service half of #544.
 *
 * The endpoints existed and were never covered by a single test, which is how a PIN of any shape —
 * a letter, a single digit, the empty string — could be hashed and stored as somebody's second
 * factor by any caller that was not the Compose screen.
 */
class TwoStepVerificationServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var attempts: InMemoryTwoStepAttemptRepository
    private lateinit var service: TwoStepVerificationService

    private val userId = TestData.USER_ID_1

    /** A trivially reversible stand-in: the real encoder's salting is not what is under test. */
    private class ReversibleEncoder : PasswordEncoder {
        override fun encode(rawPassword: CharSequence?): String = "hashed:$rawPassword"
        override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean =
            encodedPassword == "hashed:$rawPassword"
    }

    private fun user(
        pinHash: String? = null,
        email: String? = null,
        enabledAt: Instant? = null
    ) = User(
        id = userId,
        phoneNumber = TestData.PHONE_1,
        twoStepPinHash = pinHash,
        twoStepEmail = email,
        twoStepEnabledAt = enabledAt
    )

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        passwordEncoder = ReversibleEncoder()
        attempts = InMemoryTwoStepAttemptRepository()
        service = TwoStepVerificationService(userRepository, passwordEncoder, attempts)
    }

    @Test
    fun `should store the hashed PIN and the recovery email when setting up`() {
        every { userRepository.findById(userId) } returns user()
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { saved.captured }

        service.setupPin(userId, "123456", "kurtarma@example.com")

        assertEquals("hashed:123456", saved.captured.twoStepPinHash)
        assertEquals("kurtarma@example.com", saved.captured.twoStepEmail)
        assertNotNull(saved.captured.twoStepEnabledAt)
    }

    @Test
    fun `should never store the PIN in the clear`() {
        every { userRepository.findById(userId) } returns user()
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { saved.captured }

        service.setupPin(userId, "123456", null)

        assertFalse(saved.captured.twoStepPinHash == "123456")
    }

    @Test
    fun `should refuse a PIN that is not six digits`() {
        // The Compose screen enforced this and nothing else did, so the rule held only for callers
        // that happened to be the app.
        listOf("", "1", "12345", "1234567", "12345a", "abcdef", " 12345").forEach { badPin ->
            every { userRepository.findById(userId) } returns user()

            val thrown = assertThrows(BusinessException::class.java) {
                service.setupPin(userId, badPin, null)
            }

            assertEquals(ErrorCode.VALIDATION_ERROR, thrown.errorCode, "for PIN '$badPin'")
        }
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `should refuse a second setup while two-step is already on`() {
        every { userRepository.findById(userId) } returns
            user(pinHash = "hashed:123456", enabledAt = Instant.now())

        val thrown = assertThrows(BusinessException::class.java) {
            service.setupPin(userId, "654321", null)
        }

        assertEquals(ErrorCode.AUTH_2FA_ALREADY_ENABLED, thrown.errorCode)
    }

    @Test
    fun `should report two-step on and the recovery email present`() {
        every { userRepository.findById(userId) } returns
            user(pinHash = "hashed:123456", email = "kurtarma@example.com", enabledAt = Instant.now())

        val status = service.status(userId)

        assertTrue(status.enabled)
        assertTrue(status.hasRecoveryEmail)
    }

    @Test
    fun `should report two-step on but no recovery email when none was given`() {
        // `hasEmail` decides whether the screen may offer a reset. Reporting true without an
        // address would offer a route that cannot succeed.
        every { userRepository.findById(userId) } returns
            user(pinHash = "hashed:123456", enabledAt = Instant.now())

        val status = service.status(userId)

        assertTrue(status.enabled)
        assertFalse(status.hasRecoveryEmail)
    }

    @Test
    fun `should report two-step off for an account that never set a PIN`() {
        every { userRepository.findById(userId) } returns user()

        assertFalse(service.status(userId).enabled)
    }

    @Test
    fun `should clear the PIN when disabled with the right one`() {
        every { userRepository.findById(userId) } returns
            user(pinHash = "hashed:123456", email = "kurtarma@example.com", enabledAt = Instant.now())
        val saved = slot<User>()
        every { userRepository.save(capture(saved)) } answers { saved.captured }

        service.disablePin(userId, "123456")

        assertNull(saved.captured.twoStepPinHash)
        assertNull(saved.captured.twoStepEmail)
        assertNull(saved.captured.twoStepEnabledAt)
    }

    @Test
    fun `should refuse to disable with the wrong PIN`() {
        every { userRepository.findById(userId) } returns
            user(pinHash = "hashed:123456", enabledAt = Instant.now())

        val thrown = assertThrows(BusinessException::class.java) {
            service.disablePin(userId, "000000")
        }

        assertEquals(ErrorCode.AUTH_2FA_PIN_INVALID, thrown.errorCode)
        verify(exactly = 0) { userRepository.save(any()) }
    }

    @Test
    fun `should refuse to disable what is not on`() {
        every { userRepository.findById(userId) } returns user()

        val thrown = assertThrows(BusinessException::class.java) {
            service.disablePin(userId, "123456")
        }

        assertEquals(ErrorCode.AUTH_2FA_NOT_ENABLED, thrown.errorCode)
    }

    @Test
    fun `should accept the right PIN and reject a wrong one on verify`() {
        every { userRepository.findById(userId) } returns
            user(pinHash = "hashed:123456", enabledAt = Instant.now())

        assertTrue(service.verifyPin(userId, "123456"))
        assertFalse(service.verifyPin(userId, "654321"))
    }

    @Test
    fun `should lock verify out after five wrong PINs`() {
        // `/verify` answers whether a PIN is right, which makes it an oracle: unmetered, an attacker
        // holding a stolen token could find the PIN here and then use it at sign-in, where the
        // limiter would never have seen the guesses. Same counter, so the guesses come out of the
        // same five (#566).
        every { userRepository.findById(userId) } returns
            user(pinHash = "hashed:123456", enabledAt = Instant.now())

        repeat(5) { assertFalse(service.verifyPin(userId, "654321")) }

        val thrown = assertThrows(BusinessException::class.java) { service.verifyPin(userId, "123456") }
        assertEquals(ErrorCode.AUTH_2FA_LOCKED, thrown.errorCode)
    }

    @Test
    fun `should lock disable out after five wrong PINs`() {
        // Turning the second factor OFF is the most valuable thing a guessed PIN buys, so this
        // endpoint has to be metered at least as tightly as the sign-in gate.
        every { userRepository.findById(userId) } returns
            user(pinHash = "hashed:123456", enabledAt = Instant.now())

        repeat(5) {
            val wrong = assertThrows(BusinessException::class.java) { service.disablePin(userId, "654321") }
            assertEquals(ErrorCode.AUTH_2FA_PIN_INVALID, wrong.errorCode)
        }

        val thrown = assertThrows(BusinessException::class.java) { service.disablePin(userId, "123456") }
        assertEquals(ErrorCode.AUTH_2FA_LOCKED, thrown.errorCode)
    }

    @Test
    fun `should clear a stale lockout when a new PIN is set`() {
        // Reachable: lock yourself out at sign-in, then open the app on a device that is already
        // signed in. Carrying the old window over would keep punishing failures against a PIN that
        // no longer exists.
        every { userRepository.findById(userId) } returns user()
        every { userRepository.save(any()) } answers { firstArg() }
        repeat(5) { attempts.claimAttempt(userId, 5, java.time.Duration.ofSeconds(900)) }
        assertTrue(attempts.isLocked(userId))

        service.setupPin(userId, "123456", null)

        assertFalse(attempts.isLocked(userId))
    }

    @Test
    fun `should fail for a user that does not exist`() {
        every { userRepository.findById(userId) } returns null

        val thrown = assertThrows(BusinessException::class.java) { service.status(userId) }

        assertEquals(ErrorCode.USER_NOT_FOUND, thrown.errorCode)
    }
}
