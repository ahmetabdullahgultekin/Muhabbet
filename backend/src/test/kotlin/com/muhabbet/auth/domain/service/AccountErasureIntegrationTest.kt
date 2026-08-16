package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.Device
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.`in`.ManageUserDataUseCase
import com.muhabbet.auth.domain.port.out.DeviceRepository
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.redis.testcontainers.RedisContainer
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * "Delete my account" used to set `status = DELETED` and a timestamp, and nothing else. The row
 * survived with the phone number in plaintext, the discovery hash kept matching, and the devices
 * kept their push tokens — so the account was deactivated, not erased, while the privacy policy
 * promised erasure (#426).
 *
 * These assertions are about rows, not about the endpoint returning 204. The old implementation
 * returned 204 too, which is exactly why it survived.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class AccountErasureIntegrationTest {

    @Autowired private lateinit var manageUserData: ManageUserDataUseCase
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var deviceRepository: DeviceRepository
    @Autowired private lateinit var phoneHashRepository: PhoneHashRepository
    @Autowired private lateinit var entityManager: EntityManager

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

    private fun countWhereUser(table: String, column: String, userId: UUID): Long =
        entityManager.createNativeQuery("SELECT COUNT(*) FROM $table WHERE $column = :id")
            .setParameter("id", userId)
            .singleResult.let { (it as Number).toLong() }

    @Test
    @Transactional
    fun `erasure removes the identity and everything that makes the person findable`() {
        val userId = UUID.randomUUID()
        val phone = "+90500" + (1000000 + (0..8999999).random())
        userRepository.save(User(id = userId, phoneNumber = phone, displayName = "Silinecek", about = "merhaba"))
        phoneHashRepository.save(userId, "hash-of-$phone")
        deviceRepository.save(
            Device(userId = userId, deviceName = "Pixel", platform = "ANDROID", pushToken = "fcm-token-123")
        )

        // Preconditions — otherwise a passing test could just be asserting empty tables.
        assertEquals(1L, countWhereUser("phone_hashes", "user_id", userId), "phone hash was not seeded")
        assertEquals(1L, countWhereUser("devices", "user_id", userId), "device was not seeded")

        manageUserData.requestAccountDeletion(userId)
        entityManager.flush()
        entityManager.clear()

        // The discovery footprint is the one that matters most: leave it and a deleted person keeps
        // appearing in other people's contact sync.
        assertEquals(0L, countWhereUser("phone_hashes", "user_id", userId), "the contact-sync hash survived erasure")
        assertEquals(0L, countWhereUser("devices", "user_id", userId), "a device — and its push token — survived erasure")
        assertEquals(0L, countWhereUser("refresh_tokens", "user_id", userId), "a refresh token survived erasure")
        assertEquals(0L, countWhereUser("encryption_keys", "user_id", userId), "key material survived erasure")

        val erased = userRepository.findById(userId)
        assertNotNull(erased, "the row must survive: messages.sender_id references it")
        requireNotNull(erased)

        assertEquals(UserStatus.DELETED, erased.status)
        assertNotNull(erased.deletedAt)
        assertFalse(erased.phoneNumber == phone, "the phone number was still in the row in plaintext")
        assertFalse(erased.phoneNumber.startsWith("+"), "the placeholder must not look like a real number")
        assertNull(erased.displayName, "the display name survived erasure")
        assertNull(erased.about, "the about text survived erasure")
        assertNull(erased.lastSeenAt)
        assertNull(erased.twoStepPinHash)
        assertNull(erased.twoStepEmail)
    }

    @Test
    @Transactional
    fun `two erased accounts do not collide on the anonymised phone number`() {
        // users.phone_number is UNIQUE, so a constant placeholder would make the second erasure
        // throw — and the first person's deletion would silently block everyone else's.
        val ids = List(2) { UUID.randomUUID() }
        ids.forEachIndexed { i, id ->
            userRepository.save(User(id = id, phoneNumber = "+9050012345$i" + "0", displayName = "u$i"))
        }

        ids.forEach { manageUserData.requestAccountDeletion(it) }
        entityManager.flush()
        entityManager.clear()

        val placeholders = ids.mapNotNull { userRepository.findById(it)?.phoneNumber }
        assertEquals(2, placeholders.size)
        assertEquals(2, placeholders.toSet().size, "both erased accounts got the same placeholder number")
    }
}
