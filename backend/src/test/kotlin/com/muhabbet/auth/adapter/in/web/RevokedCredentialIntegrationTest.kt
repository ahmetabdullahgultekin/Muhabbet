package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.`in`.LinkDeviceUseCase
import com.muhabbet.auth.domain.port.`in`.LogoutUseCase
import com.muhabbet.auth.domain.port.`in`.ManageUserDataUseCase
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.adapter.`in`.websocket.ChatWebSocketHandler
import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.muhabbet.shared.security.JwtProvider
import com.redis.testcontainers.RedisContainer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.net.URI
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

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

    @Autowired
    private lateinit var logoutUseCase: LogoutUseCase

    @Autowired
    private lateinit var linkDeviceUseCase: LinkDeviceUseCase

    @Autowired
    private lateinit var manageUserDataUseCase: ManageUserDataUseCase

    @Autowired
    private lateinit var sessionManager: WebSocketSessionManager

    @Autowired
    private lateinit var chatWebSocketHandler: ChatWebSocketHandler

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    companion object {
        /** RQ-09: a revoked session is closed within five seconds. */
        private const val CLOSE_DEADLINE_MS = 5_000L

        /** Enough to be sure the next token's `iat` falls in a later second than the logout. */
        private const val LATER_SECOND_MS = 1_100L

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

    // ─── Logout: the access token in hand stops working, not only the refresh token ───

    /** A socket as the container would hand it to the handler, carrying [token] the 0.4 way. */
    private fun upgradeRequest(token: String): WebSocketSession = mockk(relaxed = true) {
        every { id } returns UUID.randomUUID().toString()
        every { isOpen } returns true
        every { uri } returns URI("wss://localhost/ws")
        every { handshakeHeaders } returns HttpHeaders().apply { set(HttpHeaders.AUTHORIZATION, "Bearer $token") }
        every { attributes } returns mutableMapOf()
    }

    /** A socket already registered in the real manager, as a connected client would be. */
    private fun liveSession(userId: UUID, deviceId: UUID): WebSocketSession {
        val session = mockk<WebSocketSession>(relaxed = true) {
            every { id } returns UUID.randomUUID().toString()
            every { isOpen } returns true
        }
        sessionManager.register(userId, session, deviceId, Instant.now().plusSeconds(900))
        return session
    }

    private fun revokedClose(reason: String) =
        CloseStatus(WebSocketSessionManager.CLOSE_CODE_SESSION_REVOKED, reason)

    @Test
    fun `should answer 401 to the device's existing access token when the device has logged out`() {
        val user = seedUser()
        val device = seedDevice(user.id)
        val token = bearer(user.id, device.id)

        logoutUseCase.logout(user.id, device.id)

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `should refuse a WebSocket handshake with 4403 when it reuses the access token of a logged-out device`() {
        val user = seedUser()
        val device = seedDevice(user.id)
        val token = jwtProvider.generateAccessToken(user.id, device.id)
        logoutUseCase.logout(user.id, device.id)
        val reconnect = upgradeRequest(token)

        chatWebSocketHandler.afterConnectionEstablished(reconnect)

        verify { reconnect.close(match { it.code == WebSocketSessionManager.CLOSE_CODE_SESSION_REVOKED }) }
        assertEquals(null, sessionManager.getUserId(reconnect))
    }

    @Test
    fun `should honour a token issued after the logout when the same device signs in again`() {
        val user = seedUser()
        val device = seedDevice(user.id)
        logoutUseCase.logout(user.id, device.id)
        // iat has whole-second resolution and the logout's own second is refused, so the next
        // sign-in's token must be issued in a later second.
        Thread.sleep(LATER_SECOND_MS)

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(user.id, device.id)))
            .andExpect(status().isOk)
    }

    // ─── The live socket is closed through the real transactional beans, after commit ───

    @Test
    fun `should close the device's live socket with 4403 only after the logout has committed`() {
        val user = seedUser()
        val device = seedDevice(user.id)
        val session = liveSession(user.id, device.id)
        // Read on another connection at the moment of the close: it sees only committed data, so a
        // close that ran before the commit would see no logout recorded.
        val committedAtClose = CopyOnWriteArrayList<Boolean>()
        every { session.close(any()) } answers {
            committedAtClose += CompletableFuture.supplyAsync {
                jdbcTemplate.queryForObject(
                    "SELECT logged_out_at IS NOT NULL FROM devices WHERE id = ?",
                    Boolean::class.java,
                    device.id
                ) == true
            }.get()
        }

        logoutUseCase.logout(user.id, device.id)

        verify(timeout = CLOSE_DEADLINE_MS) { session.close(revokedClose("LOGOUT")) }
        assertEquals(listOf(true), committedAtClose)
    }

    @Test
    fun `should close a revoked companion's live socket with 4403 and leave the primary's open`() {
        val user = seedUser()
        val companion = seedDevice(user.id)
        val companionSession = liveSession(user.id, companion.id)
        val otherSession = liveSession(user.id, companion.linkedByDeviceId ?: UUID.randomUUID())

        linkDeviceUseCase.revokeDevice(user.id, companion.id)

        verify(timeout = CLOSE_DEADLINE_MS) { companionSession.close(revokedClose("DEVICE_REVOKED")) }
        verify(exactly = 0) { otherSession.close(any()) }
        sessionManager.unregister(otherSession)
    }

    @Test
    fun `should close every live socket of the account with 4403 when the account is deleted`() {
        val user = seedUser()
        val device = seedDevice(user.id)
        val first = liveSession(user.id, device.id)
        val second = liveSession(user.id, UUID.randomUUID())

        manageUserDataUseCase.requestAccountDeletion(user.id)

        verify(timeout = CLOSE_DEADLINE_MS) { first.close(revokedClose("ACCOUNT_DELETED")) }
        verify(timeout = CLOSE_DEADLINE_MS) { second.close(revokedClose("ACCOUNT_DELETED")) }
    }
}
