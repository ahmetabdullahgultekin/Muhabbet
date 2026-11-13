package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.GroupInviteLinkJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataGroupInviteLinkRepository : JpaRepository<GroupInviteLinkJpaEntity, UUID> {
    fun findByInviteTokenAndIsActiveTrue(inviteToken: String): GroupInviteLinkJpaEntity?
    fun findByConversationIdAndIsActiveTrue(conversationId: UUID): List<GroupInviteLinkJpaEntity>

    /**
     * The use-count claim: one statement that both checks and increments, so the row lock taken by
     * the UPDATE serialises concurrent joins and the `use_count < max_uses` test is re-evaluated
     * against the committed count. Returns 1 when a use was taken, 0 when the link is spent or
     * inactive.
     */
    @Modifying
    @Query(
        value = """
            UPDATE group_invite_links
            SET use_count = use_count + 1
            WHERE id = :id
              AND is_active = true
              AND (max_uses IS NULL OR use_count < max_uses)
        """,
        nativeQuery = true
    )
    fun claimUse(id: UUID): Int
}
