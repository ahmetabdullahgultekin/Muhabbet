package com.muhabbet.auth.adapter.`in`.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.muhabbet.auth.domain.port.`in`.TwoStepVerificationUseCase
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.dto.RequestOtpRequest
import com.muhabbet.shared.dto.VerifyOtpRequest
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * The sign-in gate through the real endpoint, against a real database (#566).
 *
 * `TwoStepSignInGateTest` proves the service refuses. This proves the refusal survives everything
 * between the socket and the row — HTTP status mapping, transaction boundaries, and above all the
 * two counters, which is where the same feature has gone wrong before:
 *
 *  - the OTP attempt counter is incremented in its own transaction so it is not rolled back by the
 *    rejection (#266), and this test can only pass if the two-step refusal **gives that attempt
 *    back** — otherwise the second submission, carrying the very same code plus a PIN, would eat a
 *    second of the five (#688's shape, and unreachable in a mocked test);
 *  - the PIN counter has to survive its own rejection for the identical reason, so a lockout that
 *    works against a mock can still not exist against Postgres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class TwoStepPinGateIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var twoStep: TwoStepVerificationUseCase

    private val objectMapper = ObjectMapper()

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("muhabbet_test")
            withUsername("muhabbet")
            withPassword("muhabbet_test")
        }

        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("muhabbet.otp.mock-enabled") { "true" }
            registry.add("spring.data.redis.host") { redis.redisHost }
            registry.add("spring.data.redis.port") { redis.redisPort }
        }

        private const val PIN = "654321"
        private const val WRONG_PIN = "000000"
    }

    /**
     * Asks for a code and returns it.
     *
     * No cooldown collision: `findActiveByPhoneNumber` only sees rows that are unverified and
     * unexpired, so a code that has been spent does not block the next request. A code the two-step
     * gate refused is *deliberately* still live, which is the whole point of the first test.
     */
    private fun requestCode(phone: String): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/otp/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RequestOtpRequest(phone)))
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString
        return objectMapper.readTree(body).path("data").path("mockCode").asText()
    }

    private fun verify(phone: String, code: String, pin: String? = null) =
        mockMvc.perform(
            post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        VerifyOtpRequest(phone, code, "Test Device", "android", twoStepPin = pin)
                    )
                )
        )

    /** Signs in once to create the account, then switches two-step on for it. */
    private fun accountWithTwoStep(phone: String) {
        verify(phone, requestCode(phone)).andExpect(status().isOk)
        val userId = requireNotNull(userRepository.findByPhoneNumber(phone)) {
            "the first sign-in should have created $phone"
        }.id
        twoStep.setupPin(userId, PIN, null)
    }

    @Test
    fun `should refuse a sign-in with no PIN and admit the same code once the PIN is supplied`() {
        val phone = "+905000000101"
        accountWithTwoStep(phone)

        val code = requestCode(phone)

        verify(phone, code)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTH_2FA_PIN_REQUIRED"))

        // The same code, now with the PIN. This only works because the refusal above did not spend
        // the code — neither marking it verified nor charging it an attempt.
        verify(phone, code, PIN)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
    }

    @Test
    fun `should refuse a sign-in carrying the wrong PIN`() {
        val phone = "+905000000102"
        accountWithTwoStep(phone)

        val code = requestCode(phone)

        verify(phone, code, WRONG_PIN)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_2FA_PIN_INVALID"))

        // Still not spent: a wrong PIN is not a wrong code.
        verify(phone, code, PIN).andExpect(status().isOk)
    }

    @Test
    fun `should lock the PIN out after five wrong guesses and refuse the right one after that`() {
        val phone = "+905000000103"
        accountWithTwoStep(phone)

        val code = requestCode(phone)

        repeat(5) {
            verify(phone, code, WRONG_PIN)
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.code").value("AUTH_2FA_PIN_INVALID"))
        }

        // Sixth guess: refused before the hash is consulted. If the counter had been rolled back
        // with each rejection — the #266 failure, one layer up — every one of these five would have
        // read zero and this would still say AUTH_2FA_PIN_INVALID.
        verify(phone, code, WRONG_PIN)
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("AUTH_2FA_LOCKED"))

        verify(phone, code, PIN)
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("AUTH_2FA_LOCKED"))
    }

    @Test
    fun `should still refuse a wrong code when two-step is on`() {
        val phone = "+905000000104"
        accountWithTwoStep(phone)

        val code = requestCode(phone)
        val wrongCode = if (code == "000000") "111111" else "000000"

        // The second factor is in addition to the first, not instead of it: a correct PIN must not
        // let a wrong code through, and the OTP's own budget must still be charged for the guess.
        verify(phone, wrongCode, PIN)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value("AUTH_OTP_INVALID"))
    }

    @Test
    fun `should sign an account with two-step off in exactly as before`() {
        val phone = "+905000000105"

        verify(phone, requestCode(phone))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)

        val user = requireNotNull(userRepository.findByPhoneNumber(phone))
        assertEquals(null, user.twoStepEnabledAt)
    }
}
