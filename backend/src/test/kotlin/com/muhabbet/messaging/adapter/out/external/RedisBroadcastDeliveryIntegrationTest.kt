package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.adapter.`in`.websocket.WebSocketSessionManager
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Drives the Redis Pub/Sub fan-out through a real Redis, because #397 was a bug only a real
 * subscription could show: the wiring compiled, published successfully and delivered nothing.
 *
 * `MessageListenerAdapter` hands a two-argument listener method the **pattern** that matched, not
 * the channel the message arrived on, so `handleMessage` was given the literal `ws:broadcast:*`,
 * `UUID.fromString("*")` threw, and every Redis-routed message was dropped. Mocking Redis would
 * have reproduced none of that — the defect lived entirely in the container's dispatch.
 *
 * The assertion is deliberately end-to-end for this seam: publish exactly as
 * [RedisMessageBroadcaster] does, and require the frame to arrive at the recipient's session.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class RedisBroadcastDeliveryIntegrationTest {

    @Autowired
    private lateinit var sessionManager: WebSocketSessionManager

    @Autowired
    private lateinit var redisTemplate: StringRedisTemplate

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

    /** Captures what the container would have written to the socket. */
    private class CapturingSession(private val received: MutableList<String>) : WebSocketSession {
        private val id = UUID.randomUUID().toString()
        override fun getId() = id
        override fun isOpen() = true
        override fun sendMessage(message: org.springframework.web.socket.WebSocketMessage<*>) {
            received += (message as TextMessage).payload
        }

        override fun getUri(): URI? = null
        override fun getHandshakeHeaders() = org.springframework.http.HttpHeaders()
        override fun getAttributes() = mutableMapOf<String, Any>()
        override fun getPrincipal(): java.security.Principal? = null
        override fun getLocalAddress(): java.net.InetSocketAddress? = null
        override fun getRemoteAddress(): java.net.InetSocketAddress? = null
        override fun getAcceptedProtocol(): String? = null
        override fun setTextMessageSizeLimit(messageSizeLimit: Int) = Unit
        override fun getTextMessageSizeLimit() = 0
        override fun setBinaryMessageSizeLimit(messageSizeLimit: Int) = Unit
        override fun getBinaryMessageSizeLimit() = 0
        override fun getExtensions() = mutableListOf<org.springframework.web.socket.WebSocketExtension>()
        override fun close() = Unit
        override fun close(status: org.springframework.web.socket.CloseStatus) = Unit
    }

    @Test
    fun `a message published for a user reaches that user's session`() {
        val recipientId = UUID.randomUUID()
        val received = mutableListOf<String>()
        sessionManager.register(recipientId, CapturingSession(received))

        val payload = """{"type":"message.new","messageId":"${UUID.randomUUID()}"}"""
        redisTemplate.convertAndSend("${RedisMessageBroadcaster.WS_CHANNEL_PREFIX}$recipientId", payload)

        // The subscription is asynchronous; poll rather than sleep a fixed amount.
        val arrived = awaitUntil { received.isNotEmpty() }

        assertTrue(arrived, "Nothing was delivered. Before #397 the listener parsed the subscription pattern as the user id and dropped every message.")
        assertEquals(payload, received.single())
    }

    @Test
    fun `a message published for a user with no session here is not delivered to anyone else`() {
        val presentUser = UUID.randomUUID()
        val absentUser = UUID.randomUUID()
        val received = mutableListOf<String>()
        sessionManager.register(presentUser, CapturingSession(received))

        redisTemplate.convertAndSend("${RedisMessageBroadcaster.WS_CHANNEL_PREFIX}$absentUser", """{"type":"message.new"}""")

        // Give the subscription the same window the positive case needed, then require silence.
        awaitUntil { received.isNotEmpty() }
        assertTrue(received.isEmpty(), "A frame addressed to another user was delivered to this session")
    }

    private fun awaitUntil(condition: () -> Boolean): Boolean {
        val latch = CountDownLatch(1)
        repeat(50) {
            if (condition()) return true
            latch.await(100, TimeUnit.MILLISECONDS)
        }
        return condition()
    }
}
