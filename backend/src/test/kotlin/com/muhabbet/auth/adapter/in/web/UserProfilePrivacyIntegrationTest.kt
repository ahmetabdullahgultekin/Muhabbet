package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.security.JwtProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * Two-user privacy regression test for phone-number exposure + last-seen visibility
 * (Phase 0 / P0-9). Drives the REAL Spring Security filter chain.
 *
 * GET /users/{A} must NOT leak A's phone number to B, and must honour A's onlineStatusVisibility
 * (lastSeen hidden when A set it to "nobody"). GET /users/me must still return the caller's own phone.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class UserProfilePrivacyIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

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

    private fun seedUser(onlineVisibility: String = "everyone"): User {
        val id = UUID.randomUUID()
        val phone = "+90500" + (1000000 + (0..8999999).random())
        return userRepository.save(
            User(
                id = id,
                phoneNumber = phone,
                displayName = "u-$id",
                lastSeenAt = Instant.now(),
                onlineStatusVisibility = onlineVisibility
            )
        )
    }

    private fun bearer(userId: UUID): String =
        "Bearer " + jwtProvider.generateAccessToken(userId, UUID.randomUUID())

    @Test
    fun `GET users by id does not leak phone number to another user`() {
        val userA = seedUser()
        val userB = seedUser()

        mockMvc.perform(get("/api/v1/users/${userA.id}").header("Authorization", bearer(userB.id)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
            .andExpect(jsonPath("$.data.id").value(userA.id.toString()))
    }

    @Test
    fun `GET users by id hides lastSeen when target visibility is nobody`() {
        val userA = seedUser(onlineVisibility = "nobody")
        val userB = seedUser()

        mockMvc.perform(get("/api/v1/users/${userA.id}").header("Authorization", bearer(userB.id)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.lastSeenAt").doesNotExist())
            .andExpect(jsonPath("$.data.isOnline").value(false))
    }

    @Test
    fun `GET users me still returns own phone number`() {
        val me = seedUser()

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(me.id)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.phoneNumber").value(me.phoneNumber))
    }
}
