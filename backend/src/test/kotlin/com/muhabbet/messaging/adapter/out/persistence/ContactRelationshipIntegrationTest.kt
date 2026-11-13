package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.TestData
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * "Contacts" for privacy purposes, against a real database.
 *
 * Sharing a conversation used to be enough to count as someone's contact, and sharing one is
 * something anyone can arrange — subscribe to the same channel, or create a group or a 1:1 chat
 * that includes the target. A conversation now counts for a person only once they have taken part
 * in it (created it or written in it), and channels never count.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ContactRelationshipIntegrationTest {

    @Autowired private lateinit var conversations: ConversationRepository
    @Autowired private lateinit var messages: MessageRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

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
            registry.add("spring.data.redis.host") { redis.redisHost }
            registry.add("spring.data.redis.port") { redis.redisPort }
        }
    }

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        users.save(User(id = id, phoneNumber = "+90500" + (1_000_000 + (0..8_999_999).random()), displayName = "u"))
        return id
    }

    private fun conversation(type: ConversationType, createdBy: UUID, vararg memberIds: UUID): UUID {
        val id = UUID.randomUUID()
        TransactionTemplate(transactionManager).executeWithoutResult {
            conversations.save(Conversation(id = id, type = type, name = "c", createdBy = createdBy))
            memberIds.forEach { conversations.saveMember(ConversationMember(id, it, MemberRole.MEMBER)) }
        }
        return id
    }

    private fun say(conversationId: UUID, senderId: UUID) {
        TransactionTemplate(transactionManager).executeWithoutResult {
            messages.save(TestData.textMessage(id = UUID.randomUUID(), conversationId = conversationId, senderId = senderId))
        }
    }

    @Test
    fun `should not make the victim a contact of someone when they were put in a group they never spoke in`() {
        val attacker = seedUser()
        val victim = seedUser()
        conversation(ConversationType.GROUP, attacker, attacker, victim)

        assertFalse(attacker in conversations.findAllContactUserIds(victim))
        assertFalse(victim in conversations.findUsersWhoEngagedWith(attacker))
    }

    @Test
    fun `should make them contacts when the person speaks in the shared group`() {
        val creator = seedUser()
        val member = seedUser()
        val group = conversation(ConversationType.GROUP, creator, creator, member)

        say(group, member)

        assertTrue(creator in conversations.findAllContactUserIds(member))
        assertTrue(member in conversations.findUsersWhoEngagedWith(creator))
        assertTrue(creator in conversations.findUsersWhoEngagedWith(member))
    }

    @Test
    fun `should never count a channel when its owner posts to subscribers`() {
        val owner = seedUser()
        val subscriber = seedUser()
        val channel = conversation(ConversationType.CHANNEL, owner, owner, subscriber)

        say(channel, owner)
        say(channel, subscriber)

        assertFalse(subscriber in conversations.findAllContactUserIds(owner))
        assertFalse(owner in conversations.findUsersWhoEngagedWith(subscriber))
    }

    @Test
    fun `should count a direct chat for the person who started it`() {
        val starter = seedUser()
        val other = seedUser()
        conversation(ConversationType.DIRECT, starter, starter, other)

        assertTrue(other in conversations.findAllContactUserIds(starter))
        assertTrue(starter in conversations.findUsersWhoEngagedWith(other))
        assertFalse(starter in conversations.findAllContactUserIds(other))
    }
}
