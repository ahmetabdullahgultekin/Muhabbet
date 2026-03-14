package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityGroupId
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityGroupJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityMemberId
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityMemberJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataCommunityJpaRepository : JpaRepository<CommunityJpaEntity, UUID>

interface SpringDataCommunityGroupRepository : JpaRepository<CommunityGroupJpaEntity, CommunityGroupId> {
    fun findByCommunityId(communityId: UUID): List<CommunityGroupJpaEntity>

    @Query("DELETE FROM CommunityGroupJpaEntity cg WHERE cg.communityId = :communityId AND cg.conversationId = :conversationId")
    fun deleteByCommunityIdAndConversationId(communityId: UUID, conversationId: UUID)
}

interface SpringDataCommunityMemberRepository : JpaRepository<CommunityMemberJpaEntity, CommunityMemberId> {
    fun findByCommunityId(communityId: UUID): List<CommunityMemberJpaEntity>
    fun findByUserId(userId: UUID): List<CommunityMemberJpaEntity>
    fun findByCommunityIdAndUserId(communityId: UUID, userId: UUID): CommunityMemberJpaEntity?
}
