package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.security.JwtProvider
import com.redis.testcontainers.RedisContainer
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
import java.time.Instant
import java.util.UUID

/**
 * #747, through the real filter chain and a real database: a validly signed access token is refused
 * once its account is erased or its device revoked, and still honoured otherwise. Before the fix all
 * three answered 200 for the token's whole lifetime.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class RevokedCredentialIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var deviceRepository: DeviceRepository

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
    }

    private fun seedUser(status: UserStatus = UserStatus.ACTIVE): User {
        val id = UUID.randomUUID()
        return userRepository.save(
            User(
                id = id,
                phoneNumber = "+90500" + (1000000 + (0..8999999).random()),
                displayName = "u-$id",
                status = status,
                deletedAt = if (status == UserStatus.DELETED) Instant.now() else null
            )
        )
    }

    /** A companion device, linked by a real primary so the `linked_by_device_id` FK holds. */
    private fun seedDevice(userId: UUID, revokedAt: Instant? = null): Device {
        val primary = deviceRepository.save(Device(userId = userId, platform = "android", isPrimary = true))
        return deviceRepository.save(
            Device(
                userId = userId,
                platform = "web",
                linkedByDeviceId = primary.id,
                revokedAt = revokedAt
            )
        )
    }

    private fun bearer(userId: UUID, deviceId: UUID): String =
        "Bearer " + jwtProvider.generateAccessToken(userId, deviceId)

    @Test
    fun `should answer 200 when the account and device are both still valid`() {
        val user = seedUser()
        val device = seedDevice(user.id)

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(user.id, device.id)))
            .andExpect(status().isOk)
    }

    @Test
    fun `should answer 401 when the token belongs to a deleted user`() {
        val user = seedUser(status = UserStatus.DELETED)

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(user.id, UUID.randomUUID())))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should answer 401 when the token's device has been revoked and 200 for the user's other device`() {
        val user = seedUser()
        val revoked = seedDevice(user.id, revokedAt = Instant.now())
        val active = seedDevice(user.id)

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(user.id, revoked.id)))
            .andExpect(status().isUnauthorized)
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(user.id, active.id)))
            .andExpect(status().isOk)
    }
}
