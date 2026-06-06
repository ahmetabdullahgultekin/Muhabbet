package com.muhabbet.shared.security

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Actuator lockdown regression test (Phase 0 / P0-6).
 *
 * /actuator/metrics and /actuator/prometheus must NOT be publicly readable. An unauthenticated
 * caller gets 401, an ordinary (non-admin) authenticated caller gets 403. /actuator/health stays
 * public. (The admin-200 path is not asserted here because JwtProvider does not mint admin tokens;
 * it is covered by the SecurityConfig hasRole("ADMIN") rule + the operator nginx/Traefik IP gate.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ActuatorLockdownIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("muhabbet_test")
            withUsername("muhabbet")
            withPassword("muhabbet_test")
        }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("muhabbet.otp.mock-enabled") { "true" }
            registry.add("spring.data.redis.host") { "localhost" }
            registry.add("spring.data.redis.port") { "6379" }
            registry.add("spring.autoconfigure.exclude") {
                "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
            }
        }
    }

    private fun nonAdminBearer(): String =
        "Bearer " + jwtProvider.generateAccessToken(UUID.randomUUID(), UUID.randomUUID())

    @Test
    fun `prometheus is not public`() {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", nonAdminBearer()))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `metrics is not public`() {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/actuator/metrics").header("Authorization", nonAdminBearer()))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `health stays public`() {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk)
    }
}
