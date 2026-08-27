package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCommunityGroupRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCommunityJpaRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCommunityMemberRepository
import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import java.util.UUID

/**
 * The race in #446, without a database.
 *
 * `CommunityService` checks the name before it saves, but two concurrent creates can both pass that
 * check and only the second one loses at the unique index. Whatever the database says then is what
 * the caller sees, so it has to arrive as `COMMUNITY_NAME_ALREADY_EXISTS` (409) — the same answer
 * the pre-flight check gives — and not as a 500 that tells the user the server broke and invites a
 * retry that can never succeed.
 *
 * [CommunityPersistenceAdapterIntegrationTest] proves the constraint fires against a real
 * PostgreSQL. This class proves the translation is keyed on *that* constraint and lets every other
 * integrity violation through untouched, which is the half a green integration test would not show.
 */
class CommunityPersistenceAdapterTest {

    private val communityRepo = mockk<SpringDataCommunityJpaRepository>()
    private val groupRepo = mockk<SpringDataCommunityGroupRepository>()
    private val memberRepo = mockk<SpringDataCommunityMemberRepository>()
    private lateinit var adapter: CommunityPersistenceAdapter

    private val community = Community(name = "Muhabbet", createdBy = UUID.randomUUID())

    @BeforeEach
    fun setUp() {
        adapter = CommunityPersistenceAdapter(communityRepo, memberRepo, groupRepo)
    }

    @Test
    fun `should answer with the name conflict code when the unique index rejects an insert`() {
        every { communityRepo.saveAndFlush(any()) } throws uniqueNameViolation()

        val thrown = assertThrows<BusinessException> { adapter.save(community) }

        assertEquals(ErrorCode.COMMUNITY_NAME_ALREADY_EXISTS, thrown.errorCode)
    }

    @Test
    fun `should answer with the name conflict code when the unique index rejects a rename`() {
        every { communityRepo.findById(community.id) } returns
            java.util.Optional.of(CommunityJpaEntity.fromDomain(community))
        every { communityRepo.saveAndFlush(any()) } throws uniqueNameViolation()

        val thrown = assertThrows<BusinessException> { adapter.update(community.copy(name = "Mahalle")) }

        assertEquals(ErrorCode.COMMUNITY_NAME_ALREADY_EXISTS, thrown.errorCode)
    }

    @Test
    fun `should let an unrelated integrity violation through as itself`() {
        // A broken `created_by` foreign key is our bug, not the caller's, and must keep reaching
        // GlobalExceptionHandler's generic arm as a 500 with a stack trace. Translating every
        // DataIntegrityViolationException into a 409 would bury it behind a message about names.
        every { communityRepo.saveAndFlush(any()) } throws
            DataIntegrityViolationException("fk_communities_created_by violated")

        assertThrows<DataIntegrityViolationException> { adapter.save(community) }
    }

    /**
     * Shaped like what Postgres actually returns. The index name is deliberately only on the nested
     * cause and not on the exception the adapter catches — that is where the driver puts it, and a
     * matcher that inspects only the top-level message would pass a test that put it there too.
     */
    private fun uniqueNameViolation(): DataIntegrityViolationException =
        DataIntegrityViolationException(
            "could not execute statement",
            SQLException(
                "ERROR: duplicate key value violates unique constraint " +
                    "\"ux_communities_creator_name_key\""
            )
        )
}
