package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PollVote
import com.muhabbet.messaging.domain.model.Reaction
import com.muhabbet.messaging.domain.port.`in`.ResolveMessageAnnotationsUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import com.muhabbet.messaging.domain.port.out.ReactionRepository
import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * The reaction insert and the poll-vote upsert are native `ON CONFLICT` statements, and the history
 * page's annotations are three `IN` queries. All of that is SQL a mocked repository would agree with
 * whatever it said — so it runs here, against the real schema (#775, #781, #783, #336).
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class MessageAnnotationPersistenceIntegrationTest {

    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var conversationRepository: ConversationRepository
    @Autowired private lateinit var messageRepository: MessageRepository
    @Autowired private lateinit var reactionRepository: ReactionRepository
    @Autowired private lateinit var pollVoteRepository: PollVoteRepository
    @Autowired private lateinit var starredMessageRepository: StarredMessageRepository
    @Autowired private lateinit var resolveAnnotations: ResolveMessageAnnotationsUseCase
    @Autowired private lateinit var transactions: TransactionRunner

    private lateinit var alice: UUID
    private lateinit var bob: UUID
    private lateinit var conversationId: UUID

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

    @BeforeEach
    fun setUp() {
        alice = seedUser()
        bob = seedUser()
        conversationId = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = conversationId, type = ConversationType.GROUP, name = "Çay Ocağı", createdBy = alice)
        )
    }

    @Test
    fun `should write a reaction once and report the repeat as a no-op instead of failing`() {
        val messageId = seedMessage()

        val first = transactions.inTransaction { reactionRepository.addIfAbsent(reaction(messageId, alice, "👍")) }
        val second = transactions.inTransaction { reactionRepository.addIfAbsent(reaction(messageId, alice, "👍")) }

        assertTrue(first)
        assertFalse(second)
        assertEquals(1, reactionRepository.findByMessageId(messageId).size)
    }

    @Test
    fun `should replace a changed vote in place, leaving one row per voter`() {
        val messageId = seedMessage(poll = true)

        transactions.inTransaction { pollVoteRepository.upsert(PollVote(messageId = messageId, userId = alice, optionIndex = 0)) }
        transactions.inTransaction { pollVoteRepository.upsert(PollVote(messageId = messageId, userId = alice, optionIndex = 1)) }

        val votes = pollVoteRepository.findByMessageId(messageId)
        assertEquals(1, votes.size)
        assertEquals(1, votes.single().optionIndex)
    }

    @Test
    fun `should resolve stars, reactions and poll tallies per viewer for a whole page`() {
        val text = seedMessage()
        val poll = seedMessage(poll = true)
        transactions.inTransaction {
            reactionRepository.addIfAbsent(reaction(text, alice, "👍"))
            reactionRepository.addIfAbsent(reaction(text, bob, "👍"))
            reactionRepository.addIfAbsent(reaction(text, bob, "❤️"))
            pollVoteRepository.upsert(PollVote(messageId = poll, userId = bob, optionIndex = 1))
        }
        starredMessageRepository.star(alice, text)

        val page = listOfNotNull(messageRepository.findById(text), messageRepository.findById(poll))
        val forAlice = resolveAnnotations.resolveAnnotations(page, alice)
        val forBob = resolveAnnotations.resolveAnnotations(page, bob)

        assertTrue(forAlice.getValue(text).isStarred)
        assertFalse(forBob.getValue(text).isStarred, "a star belongs to the user who set it")
        assertEquals(mapOf("👍" to 2, "❤️" to 1), forAlice.getValue(text).reactions)
        assertEquals(setOf("👍"), forAlice.getValue(text).myReactions)
        assertEquals(setOf("👍", "❤️"), forBob.getValue(text).myReactions)
        assertNull(forAlice.getValue(text).poll)

        assertEquals(listOf(0, 1), forAlice.getValue(poll).poll?.counts)
        assertNull(forAlice.getValue(poll).poll?.myVote)
        assertEquals(1, forBob.getValue(poll).poll?.myVote)
    }

    private fun reaction(messageId: UUID, userId: UUID, emoji: String) =
        Reaction(messageId = messageId, userId = userId, emoji = emoji)

    private fun seedMessage(poll: Boolean = false): UUID {
        val id = UUID.randomUUID()
        transactions.inTransaction {
            messageRepository.save(
                Message(
                    id = id,
                    conversationId = conversationId,
                    senderId = alice,
                    contentType = if (poll) ContentType.POLL else ContentType.TEXT,
                    content = if (poll) """{"question":"Çay mı kahve mi?","options":["Çay","Kahve"]}""" else "merhaba",
                    clientTimestamp = Instant.now()
                )
            )
        }
        return id
    }

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 is unallocated in Turkey, so a number that leaks reaches nobody.
        val phone = "+90500" + (1_000_000 + (0..8_999_999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }
}
