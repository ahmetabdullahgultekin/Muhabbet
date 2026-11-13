package com.muhabbet.messaging.adapter.`in`.web

import com.jayway.jsonpath.JsonPath
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityMembershipUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.security.JwtProvider
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import java.util.UUID

/**
 * #902: a conversation payload may carry a participant's phone number only when that participant
 * made themselves known to the viewer one-to-one. Drives the real filter chain, the real
 * controllers and the real JPQL against Postgres — the rule lives in a query, and a mock of that
 * query would pass whatever the query said.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ParticipantPhoneVisibilityIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtProvider: JwtProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var conversationRepository: ConversationRepository
    @Autowired private lateinit var messageRepository: MessageRepository
    @Autowired private lateinit var communities: ManageCommunityUseCase
    @Autowired private lateinit var communityMembership: ManageCommunityMembershipUseCase

    /** Same seam the send path uses; see `SearchIdorIntegrationTest` for why not `@Transactional`. */
    @Autowired private lateinit var transactions: TransactionRunner

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

    // ─── Seeding ────────────────────────────────────────────

    private fun seedUser(): User {
        val id = UUID.randomUUID()
        // +90500 prefix is unallocated in Turkey — safe for tests.
        val phone = "+90500" + (1000000 + (0..8999999).random())
        return userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
    }

    private fun bearer(user: User): String =
        "Bearer " + jwtProvider.generateAccessToken(user.id, UUID.randomUUID())

    /** A DIRECT conversation opened by [creator] with [other]; nobody has written in it yet. */
    private fun seedDirect(creator: User, other: User): UUID {
        val conversationId = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = conversationId, type = ConversationType.DIRECT, createdBy = creator.id)
        )
        listOf(creator, other).forEach {
            conversationRepository.saveMember(ConversationMember(conversationId = conversationId, userId = it.id))
        }
        return conversationId
    }

    private fun seedMessage(conversationId: UUID, sender: User, scheduledFor: Instant? = null) {
        transactions.inTransaction {
            messageRepository.save(
                Message(
                    id = UUID.randomUUID(),
                    conversationId = conversationId,
                    senderId = sender.id,
                    content = "hello",
                    clientTimestamp = Instant.now(),
                    scheduledAt = scheduledFor,
                    isScheduled = scheduledFor != null
                )
            )
        }
    }

    private fun createGroup(creator: User, vararg members: User): UUID {
        val body = """{"type":"GROUP","name":"g-${UUID.randomUUID().toString().take(8)}",""" +
            """"participantIds":[${members.joinToString(",") { "\"${it.id}\"" }}]}"""
        val result = mockMvc.perform(
            post("/api/v1/conversations")
                .header("Authorization", bearer(creator))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        ).andExpect(status().isCreated).andReturn()
        return UUID.fromString(JsonPath.read<String>(result.response.contentAsString, "$.data.id"))
    }

    // ─── Reading ────────────────────────────────────────────

    /** userId → phoneNumber (null when withheld) for every participant of [conversationId], as [viewer] sees it. */
    private fun phonesSeenBy(viewer: User, conversationId: UUID): Map<String, String?> =
        participantsSeenBy(viewer)
            .filter { it.first == conversationId.toString() }
            .associate { it.second to it.third }

    /** (conversationId, participantUserId, phoneNumber) for every participant on [viewer]'s chat list. */
    private fun participantsSeenBy(viewer: User): List<Triple<String, String, String?>> {
        val json = mockMvc.perform(
            get("/api/v1/conversations").param("limit", "50").header("Authorization", bearer(viewer))
        ).andExpect(status().isOk).andReturn().response.contentAsString

        val items = JsonPath.read<List<Map<String, Any?>>>(json, "$.data.items")
        return items.flatMap { item ->
            @Suppress("UNCHECKED_CAST")
            val participants = item["participants"] as List<Map<String, Any?>>
            participants.map { p ->
                Triple(item["id"] as String, p["userId"] as String, p["phoneNumber"] as String?)
            }
        }
    }

    // ─── Tests ──────────────────────────────────────────────

    @Test
    fun `should withhold a group member's number from a co-member who is not their contact`() {
        val a = seedUser()
        val b = seedUser()
        val c = seedUser()
        // A and B are in touch one-to-one: B opened the DM and A answered.
        val dm = seedDirect(creator = b, other = a)
        seedMessage(dm, a)
        val group = createGroup(a, b, c)

        val seenByC = phonesSeenBy(c, group)
        assertNull(seenByC[a.id.toString()], "C is not A's contact and must not get A's number")
        assertNull(seenByC[b.id.toString()], "C is not B's contact and must not get B's number")
        assertEquals(c.phoneNumber, seenByC[c.id.toString()], "a viewer always gets their own number")

        // The control: the same group payload does carry the number between people in touch.
        val seenByA = phonesSeenBy(a, group)
        assertEquals(b.phoneNumber, seenByA[b.id.toString()])
        assertNull(seenByA[c.id.toString()])
        assertEquals(a.phoneNumber, phonesSeenBy(b, group)[a.id.toString()])
    }

    @Test
    fun `should not let a stranger harvest a number by opening a DM with a user id`() {
        val target = seedUser()
        val stranger = seedUser()

        val createJson = mockMvc.perform(
            post("/api/v1/conversations")
                .header("Authorization", bearer(stranger))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"type":"DIRECT","participantIds":["${target.id}"]}""")
        ).andExpect(status().isCreated).andReturn().response.contentAsString
        val dm = UUID.fromString(JsonPath.read<String>(createJson, "$.data.id"))
        val createdPhones = JsonPath.read<List<Map<String, Any?>>>(createJson, "$.data.participants")
            .associate { it["userId"] as String to it["phoneNumber"] as String? }

        assertNull(createdPhones[target.id.toString()], "the create response must not disclose the target")
        assertNull(phonesSeenBy(stranger, dm)[target.id.toString()])
        // Whoever reaches out discloses themselves — the target sees who messaged them.
        assertEquals(stranger.phoneNumber, phonesSeenBy(target, dm)[stranger.id.toString()])

        // The target answering is the target's choice to be in touch.
        seedMessage(dm, target)
        assertEquals(target.phoneNumber, phonesSeenBy(stranger, dm)[target.id.toString()])
    }

    @Test
    fun `should not count a scheduled message that has not been delivered yet`() {
        val viewer = seedUser()
        val other = seedUser()
        val dm = seedDirect(creator = viewer, other = other)
        seedMessage(dm, other, scheduledFor = Instant.now().plusSeconds(3600))

        assertNull(phonesSeenBy(viewer, dm)[other.id.toString()])
    }

    @Test
    fun `should withhold the number of a DM partner who has blocked the viewer`() {
        val viewer = seedUser()
        val blocker = seedUser()
        val dm = seedDirect(creator = blocker, other = viewer)
        seedMessage(dm, blocker)
        assertEquals(blocker.phoneNumber, phonesSeenBy(viewer, dm)[blocker.id.toString()])

        mockMvc.perform(
            post("/api/v1/moderation/blocks/${viewer.id}").header("Authorization", bearer(blocker))
        ).andExpect(status().is2xxSuccessful)

        assertNull(phonesSeenBy(viewer, dm)[blocker.id.toString()])
    }

    @Test
    fun `should never disclose numbers to members enrolled through a community announcement group`() {
        val owner = seedUser()
        val member = seedUser()
        val group = createGroup(owner, member)
        val community = communities.create("c-${UUID.randomUUID().toString().take(8)}", null, owner.id)
        communities.addGroup(community.community.id, group, owner.id)
        communityMembership.addMember(community.community.id, member.id, owner.id)

        val leaked = participantsSeenBy(member).filter { (_, userId, phone) ->
            userId != member.id.toString() && phone != null
        }
        assertTrue(leaked.isEmpty(), "member received other people's numbers: $leaked")
        assertTrue(
            participantsSeenBy(member).any { it.second == owner.id.toString() },
            "sanity: the owner must actually appear on the member's list for this to prove anything"
        )
    }

    @Test
    fun `should withhold an added member's number from the admin who added them`() {
        val admin = seedUser()
        val existing = seedUser()
        val added = seedUser()
        val group = createGroup(admin, existing)

        val json = mockMvc.perform(
            post("/api/v1/conversations/$group/members")
                .header("Authorization", bearer(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"userIds":["${added.id}"]}""")
        ).andExpect(status().isOk).andReturn().response.contentAsString

        val phones = JsonPath.read<List<Map<String, Any?>>>(json, "$.data")
            .associate { it["userId"] as String to it["phoneNumber"] as String? }
        assertTrue(added.id.toString() in phones, "sanity: the response must list the added member")
        assertNull(phones[added.id.toString()])
    }
}
