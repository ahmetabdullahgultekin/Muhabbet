package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityGroupJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityMemberJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCommunityGroupRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCommunityJpaRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataCommunityMemberRepository
import com.muhabbet.messaging.domain.model.Community
import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CommunityPersistenceAdapter(
    private val communityRepo: SpringDataCommunityJpaRepository,
    private val groupRepo: SpringDataCommunityGroupRepository,
    private val memberRepo: SpringDataCommunityMemberRepository
) : CommunityRepository {

    override fun save(community: Community): Community =
        communityRepo.save(CommunityJpaEntity.fromDomain(community)).toDomain()

    override fun findById(id: UUID): Community? =
        communityRepo.findById(id).orElse(null)?.toDomain()

    override fun update(community: Community): Community {
        val entity = communityRepo.findById(community.id).orElse(null) ?: return community
        entity.name = community.name
        entity.description = community.description
        entity.avatarUrl = community.avatarUrl
        entity.announcementGroupId = community.announcementGroupId
        entity.updatedAt = community.updatedAt
        return communityRepo.save(entity).toDomain()
    }

    override fun saveMember(member: CommunityMember): CommunityMember =
        memberRepo.save(CommunityMemberJpaEntity.fromDomain(member)).toDomain()

    override fun findMember(communityId: UUID, userId: UUID): CommunityMember? =
        memberRepo.findByCommunityIdAndUserId(communityId, userId)?.toDomain()

    override fun findMembersByCommunityId(communityId: UUID): List<CommunityMember> =
        memberRepo.findByCommunityId(communityId).map { it.toDomain() }

    override fun findCommunitiesByUserId(userId: UUID): List<Community> {
        val memberEntries = memberRepo.findByUserId(userId)
        val communityIds = memberEntries.map { it.communityId }
        return communityRepo.findAllById(communityIds).map { it.toDomain() }
    }

    override fun addGroup(group: CommunityGroup): CommunityGroup =
        groupRepo.save(CommunityGroupJpaEntity.fromDomain(group)).toDomain()

    override fun removeGroup(communityId: UUID, conversationId: UUID) {
        groupRepo.deleteByCommunityIdAndConversationId(communityId, conversationId)
    }

    override fun findGroupsByCommunityId(communityId: UUID): List<CommunityGroup> =
        groupRepo.findByCommunityId(communityId).map { it.toDomain() }
}
