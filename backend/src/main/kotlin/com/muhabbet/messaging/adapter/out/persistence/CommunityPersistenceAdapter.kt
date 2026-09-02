package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityGroupJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityMemberId
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityMemberJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCommunityGroupRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCommunityJpaRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCommunityMemberRepository
import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.port.out.CommunityGroupLinkRepository
import com.muhabbet.messaging.domain.port.out.CommunityMembershipRepository
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The `community_groups` half of [CommunityRepository], split out with its port (#446).
 *
 * Deliberately **not** a `@Component`: [CommunityPersistenceAdapter] constructs it and delegates to
 * it, so the context holds exactly one bean of type [CommunityGroupLinkRepository]. Registering this
 * as a bean too would make that type ambiguous to inject, which is a trap to leave behind for a
 * class nothing asks for by itself.
 */
class CommunityGroupLinkPersistenceAdapter(
    private val groupRepo: SpringDataCommunityGroupRepository
) : CommunityGroupLinkRepository {

    override fun addGroup(group: CommunityGroup): CommunityGroup =
        groupRepo.save(CommunityGroupJpaEntity.fromDomain(group)).toDomain()

    override fun removeGroup(communityId: UUID, conversationId: UUID) {
        groupRepo.deleteByCommunityIdAndConversationId(communityId, conversationId)
    }

    override fun findGroupsByCommunityId(communityId: UUID): List<CommunityGroup> =
        groupRepo.findByCommunityIdOrderByAddedAtAsc(communityId).map { it.toDomain() }

    override fun countGroupsByCommunityIds(communityIds: List<UUID>): Map<UUID, Int> {
        if (communityIds.isEmpty()) return emptyMap()
        return groupRepo.countByCommunityIds(communityIds).toCountById()
    }
}

/**
 * The `community_members` half of [CommunityRepository], split out with its port (#446). Not a
 * `@Component`, for the same reason as [CommunityGroupLinkPersistenceAdapter] above.
 */
class CommunityMembershipPersistenceAdapter(
    private val communityRepo: SpringDataCommunityJpaRepository,
    private val memberRepo: SpringDataCommunityMemberRepository
) : CommunityMembershipRepository {

    override fun saveMember(member: CommunityMember): CommunityMember =
        memberRepo.save(CommunityMemberJpaEntity.fromDomain(member)).toDomain()

    override fun findMember(communityId: UUID, userId: UUID): CommunityMember? =
        memberRepo.findByCommunityIdAndUserId(communityId, userId)?.toDomain()

    override fun findMembersByCommunityId(communityId: UUID): List<CommunityMember> =
        memberRepo.findByCommunityId(communityId).map { it.toDomain() }

    override fun removeMember(communityId: UUID, userId: UUID) {
        memberRepo.deleteById(CommunityMemberId(communityId, userId))
    }

    override fun findCommunitiesByUserId(userId: UUID): List<Community> {
        val memberEntries = memberRepo.findByUserId(userId)
        val communityIds = memberEntries.map { it.communityId }
        // findAllById gives no ordering guarantee, so the list would reshuffle between calls.
        return communityRepo.findAllById(communityIds).map { it.toDomain() }.sortedBy { it.createdAt }
    }

    override fun countMembersByCommunityIds(communityIds: List<UUID>): Map<UUID, Int> {
        if (communityIds.isEmpty()) return emptyMap()
        return memberRepo.countByCommunityIds(communityIds).toCountById()
    }
}

@Component
class CommunityPersistenceAdapter(
    private val communityRepo: SpringDataCommunityJpaRepository,
    memberRepo: SpringDataCommunityMemberRepository,
    groupRepo: SpringDataCommunityGroupRepository
) : CommunityRepository,
    CommunityGroupLinkRepository by CommunityGroupLinkPersistenceAdapter(groupRepo),
    CommunityMembershipRepository by CommunityMembershipPersistenceAdapter(communityRepo, memberRepo) {

    override fun save(community: Community): Community =
        translatingNameConflict {
            communityRepo.saveAndFlush(CommunityJpaEntity.fromDomain(community)).toDomain()
        }

    override fun findById(id: UUID): Community? =
        communityRepo.findById(id).orElse(null)?.toDomain()

    override fun findByCreatorAndName(creatorId: UUID, name: String): Community? =
        communityRepo.findByCreatorAndNameKey(creatorId, name)?.toDomain()

    override fun update(community: Community): Community {
        val entity = communityRepo.findById(community.id).orElse(null) ?: return community
        entity.name = community.name
        entity.description = community.description
        entity.avatarUrl = community.avatarUrl
        entity.announcementGroupId = community.announcementGroupId
        entity.updatedAt = community.updatedAt
        return translatingNameConflict { communityRepo.saveAndFlush(entity).toDomain() }
    }

    // Deleting only the `communities` row is deliberate: the FK cascade on `community_members` and
    // `community_groups` (V16) removes the membership and group-link rows for us, and neither table
    // reaches into `conversations`, so no message, member or conversation row is ever touched.
    override fun delete(id: UUID) = communityRepo.deleteById(id)
}

/**
 * Turns the unique-name index rejecting a write into the same answer the service's pre-flight
 * check gives (#446), and leaves every other integrity violation exactly as it was.
 *
 * `CommunityService` looks the name up before it writes, but two concurrent creates can both
 * find it free and only the second one loses at the index. Without this the loser gets a bare
 * `DataIntegrityViolationException`, which reaches `GlobalExceptionHandler`'s generic arm as a
 * 500 "the server broke" — telling the user to retry something that will never succeed, and
 * burying a real fault's ERROR line under it. The pre-flight check is the fast, friendly path;
 * this is the one that is actually airtight.
 *
 * `saveAndFlush` rather than `save` at both call sites is what makes the catch possible at all.
 * `CommunityJpaEntity` assigns its own UUID, so Spring Data defers the statement to flush time,
 * which without an explicit flush happens at commit — outside this block, and after the
 * `@Transactional` service method has already returned.
 *
 * The match is on the index name and nothing else. A violated `created_by` foreign key is our
 * bug, not the caller's, and has to keep arriving as a 500 with a stack trace; answering 409
 * "that name is taken" to every integrity violation would hide it. The name is read off the
 * cause chain because that is where the driver puts it — the top-level Spring exception message
 * says only "could not execute statement".
 */
private inline fun <T> translatingNameConflict(write: () -> T): T =
    try {
        write()
    } catch (ex: DataIntegrityViolationException) {
        if (generateSequence<Throwable>(ex) { it.cause }
                .any { it.message?.contains(UNIQUE_NAME_INDEX, ignoreCase = true) == true }
        ) {
            throw BusinessException(ErrorCode.COMMUNITY_NAME_ALREADY_EXISTS, cause = ex)
        }
        throw ex
    }

/**
 * The unique index created by `V23__community_name_unique_per_owner.sql`. Renaming it there without
 * changing it here silently turns every lost race back into a 500 — the catch above would stop
 * matching and nothing would fail to compile.
 */
private const val UNIQUE_NAME_INDEX = "ux_communities_creator_name_key"
