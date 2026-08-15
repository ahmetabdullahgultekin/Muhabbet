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
    fun findByCommunityIdOrderByAddedAtAsc(communityId: UUID): List<CommunityGroupJpaEntity>

    @Query("DELETE FROM CommunityGroupJpaEntity cg WHERE cg.communityId = :communityId AND cg.conversationId = :conversationId")
    fun deleteByCommunityIdAndConversationId(communityId: UUID, conversationId: UUID)

    // Batch group counts for the community list (replaces N findByCommunityId calls)
    @Query(
        """
        SELECT cg.communityId, COUNT(cg) FROM CommunityGroupJpaEntity cg
        WHERE cg.communityId IN :communityIds
        GROUP BY cg.communityId
        """
    )
    fun countByCommunityIds(communityIds: List<UUID>): List<Array<Any>>
}

interface SpringDataCommunityMemberRepository : JpaRepository<CommunityMemberJpaEntity, CommunityMemberId> {
    fun findByCommunityId(communityId: UUID): List<CommunityMemberJpaEntity>
    fun findByUserId(userId: UUID): List<CommunityMemberJpaEntity>
    fun findByCommunityIdAndUserId(communityId: UUID, userId: UUID): CommunityMemberJpaEntity?

    // Batch member counts for the community list (replaces N findByCommunityId calls)
    @Query(
        """
        SELECT cm.communityId, COUNT(cm) FROM CommunityMemberJpaEntity cm
        WHERE cm.communityId IN :communityIds
        GROUP BY cm.communityId
        """
    )
    fun countByCommunityIds(communityIds: List<UUID>): List<Array<Any>>
}
