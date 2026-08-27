package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityGroupId
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityGroupJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityMemberId
import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityMemberJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface SpringDataCommunityJpaRepository : JpaRepository<CommunityJpaEntity, UUID> {

    /**
     * The creator's community under this name, folded the way the unique index folds it (#446).
     *
     * Native rather than JPQL because `community_name_key` is a database function, defined once in
     * `V23__community_name_unique_per_owner.sql` and shared by three callers: this lookup, the
     * unique index `ux_communities_creator_name_key`, and V23's own de-duplication pass. Expressing
     * the fold a second time in Kotlin or JPQL would be a copy that can drift from the index while
     * still compiling — and when it drifts, the service reports a name as free and the insert then
     * fails, which is exactly the 500 this whole change exists to prevent.
     *
     * Hibernate flushes the persistence context before a native query, so a community written
     * earlier in the same transaction is visible here.
     */
    @Query(
        value = """
            SELECT * FROM communities
            WHERE created_by = :creatorId
              AND community_name_key(name) = community_name_key(:name)
            LIMIT 1
        """,
        nativeQuery = true
    )
    fun findByCreatorAndNameKey(creatorId: UUID, name: String): CommunityJpaEntity?
}

interface SpringDataCommunityGroupRepository : JpaRepository<CommunityGroupJpaEntity, CommunityGroupId> {
    fun findByCommunityIdOrderByAddedAtAsc(communityId: UUID): List<CommunityGroupJpaEntity>

    /**
     * Both annotations are load-bearing (#360).
     *
     * Without `@Modifying`, Spring Data treats every `@Query` as a SELECT and runs it through
     * `getResultList()`, so this DELETE threw instead of removing anything — and no test ever
     * executed it, which is how it shipped. Without `@Transactional` it then throws
     * `TransactionRequiredException` unless every caller happens to be inside a transaction of its
     * own; `CommunityService.removeGroup` is, but a repository method should not be correct only by
     * the grace of its callers. Propagation is REQUIRED, so in the service path this joins the
     * existing transaction rather than opening a second one.
     *
     * `CommunityPersistenceAdapterIntegrationTest` runs the statement against a real database, with
     * no ambient transaction, so both mistakes fail there rather than in production.
     */
    @Modifying
    @Transactional
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
