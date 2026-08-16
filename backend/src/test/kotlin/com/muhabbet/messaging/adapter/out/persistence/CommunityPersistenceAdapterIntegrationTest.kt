package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
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
 * Runs the community out-port against a real PostgreSQL, because #360 was a bug only a real
 * database could see.
 *
 * `deleteByCommunityIdAndConversationId` was a `DELETE` `@Query` with no `@Modifying`. Spring Data
 * therefore built it as a SELECT, so removing a group from a community threw instead of removing
 * anything. Every other community test mocks [CommunityRepository], and a mock agrees with whatever
 * you tell it — including a query that cannot execute. That is how it shipped, and it is why this
 * class exists rather than one more mocked assertion.
 *
 * Redis runs in a container alongside Postgres: `RedisConfig` registers a message-listener container
 * that the `test` profile's autoconfigure exclusion does not cover, so the context will not start
 * without a reachable, password-less Redis.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class CommunityPersistenceAdapterIntegrationTest {

    @Autowired
    private lateinit var communityRepository: CommunityRepository

    @Autowired
    private lateinit var conversationRepository: ConversationRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    private lateinit var creatorId: UUID
    private lateinit var communityId: UUID

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
        creatorId = seedUser()
        communityId = communityRepository.save(Community(name = "Mahalle", createdBy = creatorId)).id
    }

    @Test
    fun `should delete the link when a group is removed from a community`() {
        val conversationId = seedGroupConversation()
        communityRepository.addGroup(CommunityGroup(communityId = communityId, conversationId = conversationId))

        communityRepository.removeGroup(communityId, conversationId)

        assertTrue(communityRepository.findGroupsByCommunityId(communityId).isEmpty())
    }

    @Test
    fun `should leave the community's other groups alone when one is removed`() {
        val removed = seedGroupConversation()
        val kept = seedGroupConversation()
        communityRepository.addGroup(CommunityGroup(communityId = communityId, conversationId = removed))
        communityRepository.addGroup(CommunityGroup(communityId = communityId, conversationId = kept))

        communityRepository.removeGroup(communityId, removed)

        assertEquals(
            listOf(kept),
            communityRepository.findGroupsByCommunityId(communityId).map { it.conversationId }
        )
    }

    @Test
    fun `should leave another community's link to the same conversation alone`() {
        // The delete is keyed on both columns. Keyed on the conversation alone it would unlink the
        // group from every community that shares it.
        val conversationId = seedGroupConversation()
        val otherCommunityId = communityRepository.save(Community(name = "Okul", createdBy = creatorId)).id
        communityRepository.addGroup(CommunityGroup(communityId = communityId, conversationId = conversationId))
        communityRepository.addGroup(CommunityGroup(communityId = otherCommunityId, conversationId = conversationId))

        communityRepository.removeGroup(communityId, conversationId)

        assertTrue(communityRepository.findGroupsByCommunityId(communityId).isEmpty())
        assertEquals(1, communityRepository.findGroupsByCommunityId(otherCommunityId).size)
    }

    @Test
    fun `should do nothing when the group is not linked to the community`() {
        // The service does not check first, so an unlinked id has to be a no-op rather than a throw.
        communityRepository.removeGroup(communityId, seedGroupConversation())

        assertTrue(communityRepository.findGroupsByCommunityId(communityId).isEmpty())
    }

    @Test
    fun `should delete the row when a member leaves the community`() {
        communityRepository.saveMember(
            CommunityMember(communityId = communityId, userId = creatorId, role = MemberRole.OWNER)
        )

        communityRepository.removeMember(communityId, creatorId)

        assertNull(communityRepository.findMember(communityId, creatorId))
    }

    @Test
    fun `should change the role and keep the join date when an existing member is saved again`() {
        // How ownership succession is applied on leave: saving the same (community, user) pair must
        // update that row, not insert a second one or reset joinedAt to now.
        val joinedAt = Instant.parse("2026-01-01T00:00:00Z")
        communityRepository.saveMember(CommunityMember(communityId, creatorId, MemberRole.MEMBER, joinedAt))

        communityRepository.saveMember(CommunityMember(communityId, creatorId, MemberRole.OWNER, joinedAt))

        val members = communityRepository.findMembersByCommunityId(communityId)
        assertEquals(1, members.size)
        assertEquals(MemberRole.OWNER, members[0].role)
        assertEquals(joinedAt, members[0].joinedAt)
    }

    @Test
    fun `should cascade-delete members and group links when a community is deleted`() {
        // #407: the app code issues a single DELETE on `communities`; V16's
        // `... REFERENCES communities(id) ON DELETE CASCADE` on both link tables is what actually
        // removes these rows. A mock would agree that delete() worked even if that clause were
        // missing, so this has to run against a real Postgres to mean anything.
        val conversationId = seedGroupConversation()
        communityRepository.addGroup(CommunityGroup(communityId = communityId, conversationId = conversationId))
        communityRepository.saveMember(
            CommunityMember(communityId = communityId, userId = creatorId, role = MemberRole.OWNER)
        )

        communityRepository.delete(communityId)

        assertNull(communityRepository.findById(communityId))
        assertTrue(communityRepository.findGroupsByCommunityId(communityId).isEmpty())
        assertNull(communityRepository.findMember(communityId, creatorId))
    }

    @Test
    fun `should leave the linked conversation intact when a community is deleted`() {
        // The clause that cascades is on community_groups.community_id, not on
        // community_groups.conversation_id (nor is there any FK from conversations back to
        // communities) — so deleting a community must never take its groups' messages or members
        // with it. This is the assertion #407 asked for explicitly.
        val conversationId = seedGroupConversation()
        communityRepository.addGroup(CommunityGroup(communityId = communityId, conversationId = conversationId))

        communityRepository.delete(communityId)

        assertEquals(conversationId, conversationRepository.findById(conversationId)?.id)
    }

    @Test
    fun `should leave another community's row alone when one community is deleted`() {
        val otherCommunityId = communityRepository.save(Community(name = "Okul", createdBy = creatorId)).id
        communityRepository.saveMember(
            CommunityMember(communityId = otherCommunityId, userId = creatorId, role = MemberRole.OWNER)
        )

        communityRepository.delete(communityId)

        assertEquals("Okul", communityRepository.findById(otherCommunityId)?.name)
        assertEquals(1, communityRepository.findMembersByCommunityId(otherCommunityId).size)
    }

    @Test
    fun `should persist a new name and description when a community is updated`() {
        // `CommunityRepository.update` has zero callers, so this is the first time the column write
        // is exercised at all.
        communityRepository.update(
            Community(
                id = communityId,
                name = "Yeni Mahalle",
                description = "Yeni açıklama",
                createdBy = creatorId
            )
        )

        val reloaded = communityRepository.findById(communityId)
        assertEquals("Yeni Mahalle", reloaded?.name)
        assertEquals("Yeni açıklama", reloaded?.description)
    }

    private fun seedUser(): UUID {
        val id = UUID.randomUUID()
        // +90500 is unallocated in Turkey, so a number that leaks reaches nobody.
        val phone = "+90500" + (1_000_000 + (0..8_999_999).random())
        userRepository.save(User(id = id, phoneNumber = phone, displayName = "u-$id"))
        return id
    }

    private fun seedGroupConversation(): UUID {
        val id = UUID.randomUUID()
        conversationRepository.save(
            Conversation(id = id, type = ConversationType.GROUP, name = "Bahçe Katı", createdBy = creatorId)
        )
        return id
    }
}
