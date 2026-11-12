package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.UserStatus
import com.muhabbet.auth.domain.port.out.BlockDirectoryPort
import com.muhabbet.auth.domain.port.out.ContactSyncQuotaPort
import com.muhabbet.auth.domain.port.out.PhoneHashRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * Contact sync as an enumeration surface (#748): the daily budget, the block filter and the
 * inactive-account filter. The quota is an in-memory twin of the Redis adapter's semantics —
 * distinct hashes per account, all-or-nothing — so these tests pin the service's use of it without
 * Redis; the Redis script itself is covered by `ContactSyncEnumerationIntegrationTest`.
 */
class ContactSyncServiceTest {

    private class InMemoryContactSyncQuota(private val limit: Int) : ContactSyncQuotaPort {
        val seen = mutableMapOf<UUID, MutableSet<String>>()

        override fun tryConsume(userId: UUID, phoneHashes: Set<String>): Boolean {
            val set = seen.getOrPut(userId) { mutableSetOf() }
            if ((set + phoneHashes).size > limit) return false
            set += phoneHashes
            return true
        }
    }

    private val requester = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()

    private lateinit var phoneHashRepository: PhoneHashRepository
    private lateinit var userRepository: UserRepository
    private lateinit var blockDirectory: BlockDirectoryPort
    private lateinit var quota: InMemoryContactSyncQuota
    private lateinit var service: ContactSyncService

    private fun user(id: UUID, status: UserStatus = UserStatus.ACTIVE, deletedAt: Instant? = null) =
        User(id = id, phoneNumber = "+90500${id.hashCode()}", displayName = "u-$id", status = status, deletedAt = deletedAt)

    @BeforeEach
    fun setUp() {
        phoneHashRepository = mockk()
        userRepository = mockk()
        blockDirectory = mockk()
        quota = InMemoryContactSyncQuota(limit = 5)
        every { blockDirectory.findBlockedEitherWay(any(), any()) } returns emptySet()
        every { phoneHashRepository.findUserIdsByPhoneHashes(any()) } returns emptyMap()
        service = ContactSyncService(phoneHashRepository, userRepository, quota, blockDirectory)
    }

    @Test
    fun `should refuse with CONTACT_SYNC_LIMIT_EXCEEDED when one request carries more than 1000 hashes`() {
        val exception = assertThrows<BusinessException> {
            service.syncContacts(requester, (1..1001).map { "h$it" })
        }

        assertEquals(ErrorCode.CONTACT_SYNC_LIMIT_EXCEEDED, exception.errorCode)
    }

    @Test
    fun `should refuse and look nothing up when the daily budget of new hashes is spent`() {
        service.syncContacts(requester, listOf("h1", "h2", "h3", "h4"))

        val exception = assertThrows<BusinessException> {
            service.syncContacts(requester, listOf("h5", "h6"))
        }

        assertEquals(ErrorCode.CONTACT_SYNC_DAILY_LIMIT_EXCEEDED, exception.errorCode)
        verify(exactly = 1) { phoneHashRepository.findUserIdsByPhoneHashes(any()) }
    }

    @Test
    fun `should not charge again when the same address book is re-synced`() {
        val book = listOf("h1", "h2", "h3", "h4", "h5")

        repeat(3) { service.syncContacts(requester, book) }

        assertEquals(5, quota.seen.getValue(requester).size)
    }

    @Test
    fun `should count duplicate hashes in one request once`() {
        service.syncContacts(requester, listOf("h1", "h1", "h1", "h2"))

        assertEquals(setOf("h1", "h2"), quota.seen.getValue(requester))
    }

    @Test
    fun `should leave out users on either side of a block when matching`() {
        every { phoneHashRepository.findUserIdsByPhoneHashes(any()) } returns mapOf("ha" to alice, "hb" to bob)
        every { blockDirectory.findBlockedEitherWay(requester, setOf(alice, bob)) } returns setOf(bob)
        every { userRepository.findAllByIds(listOf(alice)) } returns listOf(user(alice))

        val matched = service.syncContacts(requester, listOf("ha", "hb"))

        assertEquals(listOf(alice.toString()), matched.map { it.userId })
    }

    @Test
    fun `should leave out deleted and suspended accounts when matching`() {
        val carol = UUID.randomUUID()
        every { phoneHashRepository.findUserIdsByPhoneHashes(any()) } returns
            mapOf("ha" to alice, "hb" to bob, "hc" to carol)
        every { userRepository.findAllByIds(any()) } returns listOf(
            user(alice),
            user(bob, status = UserStatus.DELETED, deletedAt = Instant.now()),
            user(carol, status = UserStatus.SUSPENDED),
        )

        val matched = service.syncContacts(requester, listOf("ha", "hb", "hc"))

        assertEquals(listOf(alice.toString()), matched.map { it.userId })
    }

    @Test
    fun `should never return the requester when their own hash is in the book`() {
        every { phoneHashRepository.findUserIdsByPhoneHashes(any()) } returns mapOf("hme" to requester)

        val matched = service.syncContacts(requester, listOf("hme"))

        assertTrue(matched.isEmpty())
    }
}
