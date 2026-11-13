package com.muhabbet.messaging.adapter.`in`.web

import com.jayway.jsonpath.JsonPath
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.MessageCursor
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.security.JwtProvider
import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
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
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Message history through the real stack — Security filter chain, controller, service, JPQL,
 * PostgreSQL — for the three defects that only show against a real database:
 *
 * - **#874** a member reads only the part of a group their membership opens, on every read path:
 *   history, background sync, shared media, search (in-conversation and global), message info and
 *   the inbox preview. A re-added member does not see the gap; a direct chat is unchanged.
 * - **#750** the keyset over `(server_timestamp, id)` returns every row exactly once even when a
 *   whole page shares one timestamp, in both directions; scheduled messages that have not gone out
 *   are in nobody's history.
 * - **#876** `direction=after` is honoured and a malformed cursor is a 400, not the newest page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MessageHistoryIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jwtProvider: JwtProvider
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var conversationRepository: ConversationRepository
    @Autowired private lateinit var messageRepository: MessageRepository
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

        /** Microseconds: what `timestamptz` stores, so values read back compare equal. */
        private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)
    }

    // ---- seeding ----------------------------------------------------------------------------

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 prefix is unallocated in Turkey — safe for tests.
        userRepository.save(User(id = id, phoneNumber = "+90500" + (1000000 + (0..8999999).random()), displayName = "u-$id"))
        return id
    }

    private fun bearer(userId: UUID) = "Bearer " + jwtProvider.generateAccessToken(userId, UUID.randomUUID())

    private fun seedConversation(type: ConversationType, owner: UUID): UUID {
        val id = UUID.randomUUID()
        conversationRepository.save(Conversation(id = id, type = type, name = "c-$id", createdBy = owner))
        return id
    }

    private fun join(conversationId: UUID, userId: UUID, at: Instant, role: MemberRole = MemberRole.MEMBER) {
        conversationRepository.saveMember(ConversationMember(conversationId = conversationId, userId = userId, role = role, joinedAt = at))
    }

    private fun send(
        conversationId: UUID,
        sender: UUID,
        content: String,
        at: Instant,
        contentType: ContentType = ContentType.TEXT,
        scheduledFor: Instant? = null,
        id: UUID = UUID.randomUUID()
    ): UUID {
        transactions.inTransaction {
            messageRepository.save(
                Message(
                    id = id,
                    conversationId = conversationId,
                    senderId = sender,
                    content = content,
                    contentType = contentType,
                    mediaUrl = if (contentType == ContentType.IMAGE) "https://cdn.example/$id.jpg" else null,
                    serverTimestamp = at,
                    clientTimestamp = at,
                    scheduledAt = scheduledFor,
                    isScheduled = scheduledFor != null
                )
            )
        }
        return id
    }

    private fun getJson(path: String, user: UUID, vararg params: Pair<String, String>): String {
        val request = get(path).header("Authorization", bearer(user))
        params.forEach { (k, v) -> request.param(k, v) }
        return mockMvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString
    }

    private fun contents(json: String): List<String> = JsonPath.read(json, "$.data.items[*].content")

    // ---- #874 ---------------------------------------------------------------------------------

    @Nested
    inner class HistoryVisibility {

        /** A group with two messages before [newcomer] joined and two after. */
        private inner class Scenario {
            val owner = seedUser()
            val founder = seedUser()
            val newcomer = seedUser()
            val tag = UUID.randomUUID().toString().take(8)
            val t0 = now().minus(2, ChronoUnit.HOURS)
            val joined = now().minus(1, ChronoUnit.HOURS)
            val group = seedConversation(ConversationType.GROUP, owner)
            val preText: UUID
            val preImage: UUID

            init {
                join(group, owner, t0, MemberRole.OWNER)
                join(group, founder, t0)
                preText = send(group, owner, "pre secret $tag", t0.plusSeconds(60))
                preImage = send(group, owner, "pre photo $tag", t0.plusSeconds(120), ContentType.IMAGE)
                join(group, newcomer, joined)
                send(group, owner, "post hello $tag", joined.plusSeconds(60))
                send(group, owner, "post photo $tag", joined.plusSeconds(120), ContentType.IMAGE)
            }
        }

        @Test
        fun `should show a new member only what was said after they joined`() {
            val s = Scenario()
            assertThat(contents(getJson("/api/v1/conversations/${s.group}/messages", s.newcomer)))
                .containsExactly("post photo ${s.tag}", "post hello ${s.tag}")
            assertThat(contents(getJson("/api/v1/conversations/${s.group}/messages", s.founder)))
                .hasSize(4)
        }

        @Test
        fun `should keep pre-join messages out of background sync`() {
            val s = Scenario()
            val since = s.t0.minusSeconds(1).toString()
            assertThat(contents(getJson("/api/v1/messages/since", s.newcomer, "timestamp" to since)))
                .containsExactly("post hello ${s.tag}", "post photo ${s.tag}")
            assertThat(contents(getJson("/api/v1/messages/since", s.founder, "timestamp" to since))).hasSize(4)
        }

        @Test
        fun `should keep pre-join media out of the shared media grid`() {
            val s = Scenario()
            assertThat(contents(getJson("/api/v1/conversations/${s.group}/media", s.newcomer)))
                .containsExactly("post photo ${s.tag}")
            assertThat(contents(getJson("/api/v1/conversations/${s.group}/media", s.founder))).hasSize(2)
        }

        @Test
        fun `should keep pre-join messages out of both searches`() {
            val s = Scenario()
            assertThat(contents(getJson("/api/v1/search/messages", s.newcomer, "q" to s.tag)))
                .containsExactlyInAnyOrder("post hello ${s.tag}", "post photo ${s.tag}")
            assertThat(contents(getJson("/api/v1/search/messages", s.newcomer, "q" to s.tag, "conversationId" to s.group.toString())))
                .containsExactlyInAnyOrder("post hello ${s.tag}", "post photo ${s.tag}")
            assertThat(contents(getJson("/api/v1/search/messages", s.founder, "q" to s.tag))).hasSize(4)
        }

        @Test
        fun `should answer message info for a pre-join message as not found`() {
            val s = Scenario()
            mockMvc.perform(get("/api/v1/messages/${s.preText}/info").header("Authorization", bearer(s.newcomer)))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.error.code").value("MSG_NOT_FOUND"))
            mockMvc.perform(get("/api/v1/messages/${s.preText}/info").header("Authorization", bearer(s.founder)))
                .andExpect(status().isOk)
        }

        @Test
        fun `should not preview a pre-join message in the inbox`() {
            val owner = seedUser()
            val newcomer = seedUser()
            val group = seedConversation(ConversationType.GROUP, owner)
            join(group, owner, now().minusSeconds(600), MemberRole.OWNER)
            send(group, owner, "said before you came", now().minusSeconds(300))
            join(group, newcomer, now().minusSeconds(60))

            val json = getJson("/api/v1/conversations", newcomer, "limit" to "100")
            val rows: List<Map<String, Any?>> = JsonPath.read(json, "$.data.items[?(@.id == '$group')]")
            assertThat(rows).hasSize(1)
            assertThat(rows.single()["lastMessagePreview"]).isNull()
        }

        @Test
        fun `should not show a re-added member what was said while they were out`() {
            val owner = seedUser()
            val member = seedUser()
            val tag = UUID.randomUUID().toString().take(8)
            val group = seedConversation(ConversationType.GROUP, owner)
            join(group, owner, now().minusSeconds(3600), MemberRole.OWNER)
            join(group, member, now().minusSeconds(3600))
            send(group, owner, "first stint $tag", now().minusSeconds(3000))
            transactions.inTransaction { conversationRepository.removeMember(group, member) }
            send(group, owner, "while away $tag", now().minusSeconds(2000))
            join(group, member, now().minusSeconds(1000))
            send(group, owner, "second stint $tag", now().minusSeconds(500))

            assertThat(contents(getJson("/api/v1/conversations/$group/messages", member)))
                .containsExactly("second stint $tag")
        }

        @Test
        fun `should leave a direct conversation's history unbounded`() {
            val a = seedUser()
            val b = seedUser()
            val dm = seedConversation(ConversationType.DIRECT, a)
            // Membership rows stamped after the messages: a GROUP would hide all three, a DM must not.
            send(dm, a, "dm one", now().minusSeconds(300))
            send(dm, b, "dm two", now().minusSeconds(200))
            send(dm, a, "dm three", now().minusSeconds(100))
            join(dm, a, now())
            join(dm, b, now())

            assertThat(contents(getJson("/api/v1/conversations/$dm/messages", b)))
                .containsExactly("dm three", "dm two", "dm one")
        }
    }

    // ---- #750 / #876 ----------------------------------------------------------------------------

    @Nested
    inner class KeysetPaging {

        private fun pageThrough(conversation: UUID, user: UUID, direction: String, start: String?, limit: Int): List<String> {
            val seen = mutableListOf<String>()
            var cursor = start
            var guard = 0
            do {
                val params = buildList {
                    add("limit" to limit.toString())
                    add("direction" to direction)
                    cursor?.let { add("cursor" to it) }
                }.toTypedArray()
                val json = getJson("/api/v1/conversations/$conversation/messages", user, *params)
                seen += JsonPath.read<List<String>>(json, "$.data.items[*].id")
                val hasMore: Boolean = JsonPath.read(json, "$.data.hasMore")
                cursor = JsonPath.read<String?>(json, "$.data.nextCursor")
                if (hasMore) assertThat(cursor).isNotNull()
            } while (hasMore && ++guard < 50)
            return seen
        }

        @Test
        fun `should return 120 messages sharing one timestamp exactly once across pages of 50`() {
            // The case `server_timestamp < :before` loses: the page boundary falls inside the run of
            // equal timestamps, and everything left in the run is neither on this page nor the next.
            val a = seedUser()
            val dm = seedConversation(ConversationType.DIRECT, a)
            join(dm, a, now().minusSeconds(3600))
            val instant = now().minusSeconds(60)
            val ids = (1..120).map { send(dm, a, "tie $it", instant) }

            val backwards = pageThrough(dm, a, "before", null, 50)
            assertThat(backwards).hasSize(120).doesNotHaveDuplicates()
            assertThat(backwards.map(UUID::fromString)).containsExactlyInAnyOrderElementsOf(ids)
            assertThat(backwards).isEqualTo(ids.map { it.toString() }.sortedDescending())
        }

        @Test
        fun `should walk forward with direction after, oldest first, without skipping`() {
            val a = seedUser()
            val dm = seedConversation(ConversationType.DIRECT, a)
            join(dm, a, now().minusSeconds(3600))
            val base = now().minusSeconds(600)
            // Runs of equal timestamps of different lengths, so boundaries land everywhere.
            val ids = (0 until 37).map { send(dm, a, "m$it", base.plusMillis((it / 4).toLong())) }
            // PostgreSQL orders uuid bytewise, which is the canonical string's order — not
            // java.util.UUID.compareTo, which compares signed longs.
            val ordered = ids.withIndex()
                .sortedWith(compareBy({ it.index / 4 }, { it.value.toString() }))
                .map { it.value.toString() }

            val newestFirst = getJson("/api/v1/conversations/$dm/messages", a, "limit" to "100")
            assertThat(JsonPath.read<List<String>>(newestFirst, "$.data.items[*].id")).isEqualTo(ordered.reversed())

            // Resume after the oldest message, as a client filling a gap from its last known row.
            val cursor = MessageCursor(base, UUID.fromString(ordered.first())).encode()
            val forwards = pageThrough(dm, a, "after", cursor, 5)
            assertThat(forwards).isEqualTo(ordered.drop(1))
        }

        @Test
        fun `should keep not-yet-due scheduled messages out of history and sync`() {
            val a = seedUser()
            val b = seedUser()
            val dm = seedConversation(ConversationType.DIRECT, a)
            join(dm, a, now().minusSeconds(3600))
            join(dm, b, now().minusSeconds(3600))
            val tag = UUID.randomUUID().toString().take(8)
            send(dm, a, "now $tag", now().minusSeconds(30))
            send(dm, a, "later $tag", now().minusSeconds(20), scheduledFor = now().plus(1, ChronoUnit.DAYS))

            assertThat(contents(getJson("/api/v1/conversations/$dm/messages", b))).containsExactly("now $tag")
            assertThat(contents(getJson("/api/v1/messages/since", b, "timestamp" to now().minusSeconds(60).toString())))
                .contains("now $tag").doesNotContain("later $tag")
        }

        @Test
        fun `should refuse a malformed cursor with 400`() {
            val a = seedUser()
            val dm = seedConversation(ConversationType.DIRECT, a)
            join(dm, a, now())
            mockMvc.perform(
                get("/api/v1/conversations/$dm/messages").param("cursor", "%%%").header("Authorization", bearer(a))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.error.code").value("MSG_INVALID_CURSOR"))
        }
    }
}
