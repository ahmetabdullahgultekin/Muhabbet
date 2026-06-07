package com.muhabbet.shared.security

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment

/**
 * Fail-closed startup guard for the JWT signing secret (Phase 1 / finding #3).
 *
 * The dev default in application.yml is world-known and forgeable; booting with it — or with a
 * sub-256-bit secret — would let anyone mint valid tokens. validateSecret() must abort boot.
 */
class JwtProviderSecretGuardTest {

    private val devDefault = "dev-secret-change-in-production-min-256-bits-long-key-here"

    private fun props(secret: String) = JwtProperties(
        secret = secret,
        accessTokenExpiry = 900,
        refreshTokenExpiry = 2592000,
        issuer = "muhabbet"
    )

    private fun prodEnv() = MockEnvironment().apply { setActiveProfiles("prod") }

    @Test
    fun `validateSecret should fail boot when secret is still the dev default`() {
        val provider = JwtProvider(props(devDefault), prodEnv())

        val ex = assertThrows(IllegalStateException::class.java) { provider.validateSecret() }
        assert(ex.message!!.contains("dev default"))
    }

    @Test
    fun `validateSecret should fail boot when secret is shorter than 32 bytes`() {
        val provider = JwtProvider(props("too-short-secret"), MockEnvironment())

        val ex = assertThrows(IllegalStateException::class.java) { provider.validateSecret() }
        assert(ex.message!!.contains("too short"))
    }

    @Test
    fun `validateSecret should pass when secret is unique and at least 32 bytes`() {
        val strong = "a-properly-long-production-secret-with-enough-entropy-1234567890"
        val provider = JwtProvider(props(strong), prodEnv())

        assertDoesNotThrow { provider.validateSecret() }
    }
}
