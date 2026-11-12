package com.muhabbet.auth.adapter.`in`.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.muhabbet.auth.domain.model.AgeAssuranceLevel
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.dto.DeclareBirthDateRequest
import com.muhabbet.shared.dto.RequestOtpRequest
import com.muhabbet.shared.dto.VerifyOtpRequest
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import java.util.UUID

/**
 * The age gate (#751) through the real endpoints against Postgres: V30's columns, the error mapping,
 * the OTP attempt refund on a missing date, and the declare-once rule for accounts that predate it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class AgeGateIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

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

        private const val ADULT = "1990-05-01"
    }

    private val underFifteen = LocalDate.now().minusYears(14).toString()

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

    private fun verify(phone: String, code: String, birthDate: String?): ResultActions =
        mockMvc.perform(
            post("/api/v1/auth/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        VerifyOtpRequest(phone, code, "Test Device", "android", birthDate = birthDate)
                    )
                )
        )

    private fun accessTokenOf(result: ResultActions): String =
        objectMapper.readTree(result.andReturn().response.contentAsString)
            .path("data").path("accessToken").asText()

    private fun declare(token: String, birthDate: String): ResultActions =
        mockMvc.perform(
            patch("/api/v1/users/me/birth-date")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(DeclareBirthDateRequest(birthDate)))
        )

    /** An account as every account was before V30: no date of birth at all. */
    private fun legacyAccount(phone: String) {
        userRepository.save(User(id = UUID.randomUUID(), phoneNumber = phone))
    }

    @Test
    fun `should refuse registration and create no user when declared age is under fifteen`() {
        val phone = "+905000000201"

        verify(phone, requestCode(phone), underFifteen)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTH_AGE_BELOW_MINIMUM"))

        assertNull(userRepository.findByPhoneNumber(phone))
    }

    @Test
    fun `should accept the same code with a date when registration first omitted it`() {
        val phone = "+905000000202"
        val code = requestCode(phone)

        verify(phone, code, null)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("AUTH_AGE_REQUIRED"))
        assertNull(userRepository.findByPhoneNumber(phone))

        // Only possible because the refusal handed the attempt back and did not spend the code.
        verify(phone, code, ADULT)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isNewUser").value(true))
            .andExpect(jsonPath("$.data.ageVerificationRequired").value(false))

        val user = requireNotNull(userRepository.findByPhoneNumber(phone))
        assertEquals(LocalDate.parse(ADULT), user.birthDate)
        assertEquals(AgeAssuranceLevel.SELF_DECLARED, user.ageAssuranceLevel)
        assertNotNull(user.ageAssuredAt)
    }

    @Test
    fun `should refuse a malformed birth date as invalid input`() {
        val phone = "+905000000203"

        verify(phone, requestCode(phone), "01.05.1990")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error.code").value("AUTH_BIRTH_DATE_INVALID"))
    }

    @Test
    fun `should sign in a legacy account with the flag set and let it declare its date once`() {
        val phone = "+905000000204"
        legacyAccount(phone)

        val signIn = verify(phone, requestCode(phone), null)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.isNewUser").value(false))
            .andExpect(jsonPath("$.data.ageVerificationRequired").value(true))
        val token = accessTokenOf(signIn)

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.ageVerificationRequired").value(true))

        declare(token, ADULT)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.ageVerificationRequired").value(false))

        // Immutable: a second declaration, even a plausible one, is refused and changes nothing.
        declare(token, "1985-01-01")
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error.code").value("AUTH_BIRTH_DATE_ALREADY_SET"))
        assertEquals(LocalDate.parse(ADULT), userRepository.findByPhoneNumber(phone)?.birthDate)

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer $token"))
            .andExpect(jsonPath("$.data.ageVerificationRequired").value(false))
    }

    @Test
    fun `should keep refusing a legacy account that declared an under-age date first`() {
        val phone = "+905000000205"
        legacyAccount(phone)
        val token = accessTokenOf(verify(phone, requestCode(phone), null).andExpect(status().isOk))

        declare(token, underFifteen)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTH_AGE_BELOW_MINIMUM"))

        // The refusal was recorded, so trying an earlier year does not get round it.
        declare(token, ADULT)
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.error.code").value("AUTH_AGE_BELOW_MINIMUM"))

        val user = requireNotNull(userRepository.findByPhoneNumber(phone))
        assertEquals(LocalDate.parse(underFifteen), user.birthDate)
        assertNull(user.ageAssuranceLevel)
    }
}
