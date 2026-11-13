package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.`in`.LogoutUseCase
import com.muhabbet.auth.domain.port.`in`.RegisterPushTokenUseCase
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.adapter.out.external.MessageSourceNotificationTextAdapter
import com.muhabbet.messaging.adapter.out.external.NotificationTextConfig
import com.muhabbet.messaging.adapter.out.external.OfflinePushSender
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PushNotification
import com.muhabbet.messaging.domain.port.out.PushNotificationPort
import com.muhabbet.messaging.domain.service.PushNotificationComposer
import com.muhabbet.shared.event.PushTokenRejectedEvent
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * #908 against a real database: a push token belongs to exactly one device.
 *
 * Every assertion reads rows (or what the fan-out actually hands to the provider), not return
 * values — the bug was a row nobody cleared, invisible to anything that only checks a 204.
 *
 * No `@Transactional` on the tests: each use case has to commit for real, both so the unique index
 * is exercised the way production exercises it and so the concurrency test has two transactions to
 * race. Tokens and phone numbers are random per test so the tests cannot see each other's rows.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class PushTokenOwnershipIntegrationTest {

    @Autowired private lateinit var registerPushToken: RegisterPushTokenUseCase
    @Autowired private lateinit var logout: LogoutUseCase
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var deviceRepository: DeviceRepository
    @Autowired private lateinit var eventPublisher: ApplicationEventPublisher
    @Autowired private lateinit var transactionManager: PlatformTransactionManager
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

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

    /** Records what the fan-out would hand to FCM, so "no push to T" is observed, not inferred. */
    private class RecordingPushPort : PushNotificationPort {
        val tokens = mutableListOf<String>()
        override fun sendPush(pushToken: String, notification: PushNotification) {
            tokens += pushToken
        }
    }

    private fun newToken() = "fcm:" + UUID.randomUUID()

    /** A signed-up account with one Android device, the shape sign-in leaves behind. */
    private fun accountWithDevice(): Device {
        val userId = UUID.randomUUID()
        userRepository.save(User(id = userId, phoneNumber = "+90500" + (1000000 + (0..8999999).random())))
        return deviceRepository.save(
            Device(userId = userId, platform = "android", lastActiveAt = Instant.now(), isPrimary = true)
        )
    }

    private fun tokenOf(device: Device): String? = deviceRepository.findById(device.id)?.pushToken

    private fun pushesTo(userId: UUID): List<String> {
        val port = RecordingPushPort()
        val composer = PushNotificationComposer(
            MessageSourceNotificationTextAdapter(NotificationTextConfig().notificationMessageSource())
        )
        val message = Message(
            id = UUID.randomUUID(),
            conversationId = UUID.randomUUID(),
            senderId = UUID.randomUUID(),
            content = "gizli",
            clientTimestamp = Instant.now()
        )
        OfflinePushSender(deviceRepository, port, composer)
            .sendToAll(listOf(userId), message, senderName = "Ayşe", conversation = null)
        return port.tokens
    }

    @Test
    fun `a second account registering the same phone's token takes it from the first`() {
        // The scenario in the issue: A used the phone, B signs in on it. B's app registers the
        // phone's one FCM token; before #908 A's row kept it too and A's previews reached B.
        val a = accountWithDevice()
        val b = accountWithDevice()
        val phoneToken = newToken()

        registerPushToken.registerPushToken(a.userId, a.id, phoneToken, "tr")
        registerPushToken.registerPushToken(b.userId, b.id, phoneToken, "tr")

        assertNull(tokenOf(a), "the first account kept the phone's token")
        assertEquals(phoneToken, tokenOf(b))
        assertTrue(pushesTo(a.userId).isEmpty(), "a message to the first account was still pushed to the phone")
        assertEquals(listOf(phoneToken), pushesTo(b.userId))
    }

    @Test
    fun `logout then a new account on the same phone leaves the first account with no push address`() {
        // The acceptance criterion as written: A registers T and logs out; B logs in and registers T.
        val a = accountWithDevice()
        val b = accountWithDevice()
        val phoneToken = newToken()
        registerPushToken.registerPushToken(a.userId, a.id, phoneToken, null)

        logout.logout(a.userId, a.id)
        assertNull(tokenOf(a), "logout left the token on the signed-out device")

        registerPushToken.registerPushToken(b.userId, b.id, phoneToken, null)
        assertNull(tokenOf(a))
        assertEquals(phoneToken, tokenOf(b))
        assertTrue(pushesTo(a.userId).isEmpty())
    }

    @Test
    fun `logout clears only the signed-out device's token`() {
        val a = accountWithDevice()
        val other = accountWithDevice()
        val aToken = newToken()
        val otherToken = newToken()
        registerPushToken.registerPushToken(a.userId, a.id, aToken, null)
        registerPushToken.registerPushToken(other.userId, other.id, otherToken, null)

        logout.logout(a.userId, a.id)

        assertNull(tokenOf(a))
        assertEquals(otherToken, tokenOf(other))
    }

    @Test
    fun `re-registering a token on the device that already holds it keeps it there`() {
        // The app registers on every sign-in and every launch; the common case must be a no-op.
        val a = accountWithDevice()
        val token = newToken()

        registerPushToken.registerPushToken(a.userId, a.id, token, null)
        registerPushToken.registerPushToken(a.userId, a.id, token, "en")

        assertEquals(token, tokenOf(a))
        assertEquals("en", deviceRepository.findById(a.id)?.locale)
    }

    @Test
    fun `a token the provider rejected is forgotten`() {
        // The FCM adapter publishes this for UNREGISTERED / SENDER_ID_MISMATCH / INVALID_ARGUMENT;
        // the auth module must hear it and clear the row, through the real listener wiring.
        val a = accountWithDevice()
        val token = newToken()
        registerPushToken.registerPushToken(a.userId, a.id, token, null)

        eventPublisher.publishEvent(PushTokenRejectedEvent(token, reason = "UNREGISTERED"))

        assertNull(tokenOf(a), "a token FCM rejected for good was kept and would be retried forever")
        assertTrue(pushesTo(a.userId).isEmpty())
    }

    @Test
    fun `the database refuses a second holder of one token whatever writes it`() {
        // The index is the backstop for any future write path that bypasses the service.
        val a = accountWithDevice()
        val b = accountWithDevice()
        val token = newToken()
        registerPushToken.registerPushToken(a.userId, a.id, token, null)

        assertThrows<DataIntegrityViolationException> {
            jdbcTemplate.update("UPDATE devices SET push_token = ? WHERE id = ?", token, b.id)
        }
    }

    @Test
    fun `signing in on a device read before its token moved does not take the token back`() {
        // Sign-in reads the device row, then writes it. If another account's registration moves the
        // phone's token in between, writing the whole row back would reinstate the token on the
        // stale row — a second holder the index refuses, so sign-in would fail with a 500.
        val a = accountWithDevice()
        val b = accountWithDevice()
        val token = newToken()
        registerPushToken.registerPushToken(a.userId, a.id, token, null)
        val readBySignIn = deviceRepository.findById(a.id) ?: error("device vanished")
        assertEquals(token, readBySignIn.pushToken)

        registerPushToken.registerPushToken(b.userId, b.id, token, null) // moves it to b

        // What sign-in used to do with that row: refused by the index.
        assertThrows<DataIntegrityViolationException> {
            deviceRepository.save(readBySignIn.copy(deviceName = "Pixel 9", lastActiveAt = Instant.now()))
        }
        // What it does now.
        TransactionTemplate(transactionManager).executeWithoutResult {
            assertEquals(1, deviceRepository.recordSignIn(a.id, "Pixel 9", Instant.now()))
        }

        assertNull(tokenOf(a), "sign-in reinstated a token another device had taken")
        assertEquals(token, tokenOf(b))
        assertEquals("Pixel 9", deviceRepository.findById(a.id)?.deviceName)
    }

    @Test
    fun `two devices registering one token at the same moment end with the later one holding it`() {
        // Without the per-token lock neither transaction can see the other's uncommitted row, so
        // neither detaches anything and the second fails on the unique index instead of winning.
        val a = accountWithDevice()
        val b = accountWithDevice()
        val token = newToken()
        val firstHasWritten = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val first = pool.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    registerPushToken.registerPushToken(a.userId, a.id, token, null)
                    firstHasWritten.countDown()
                    // Hold the transaction — and the lock — open while the second one starts.
                    releaseFirst.await(10, TimeUnit.SECONDS)
                }
            }
            assertTrue(firstHasWritten.await(10, TimeUnit.SECONDS))
            val second = pool.submit {
                registerPushToken.registerPushToken(b.userId, b.id, token, null)
            }
            // Give the second registration time to reach the lock before the first commits.
            Thread.sleep(SECOND_REGISTRATION_HEAD_START_MS)
            releaseFirst.countDown()

            first.get(10, TimeUnit.SECONDS)
            second.get(10, TimeUnit.SECONDS) // throws if the second failed on the index
        } finally {
            releaseFirst.countDown()
            pool.shutdownNow()
        }

        assertNull(tokenOf(a))
        assertEquals(token, tokenOf(b))
    }
}

private const val SECOND_REGISTRATION_HEAD_START_MS = 500L
