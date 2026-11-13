package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.DirectConversationLookupId
import com.muhabbet.messaging.adapter.out.persistence.entity.DirectConversationLookupJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface SpringDataDirectConversationLookupRepository : JpaRepository<DirectConversationLookupJpaEntity, DirectConversationLookupId> {
    fun findByUserIdLowAndUserIdHigh(userIdLow: UUID, userIdHigh: UUID): DirectConversationLookupJpaEntity?

    /**
     * `ON CONFLICT DO NOTHING` on the pair's primary key (#899). A concurrent uncommitted insert of
     * the same pair blocks this statement until it commits (then this inserts nothing) or rolls back
     * (then this inserts). Flushes first so the conversation row the FK points at is in the database.
     */
    @Modifying(flushAutomatically = true)
    @Query(
        nativeQuery = true,
        value = """
        INSERT INTO direct_conversation_lookup (user_id_low, user_id_high, conversation_id)
        VALUES (:low, :high, :conversationId)
        ON CONFLICT (user_id_low, user_id_high) DO NOTHING
        """
    )
    fun insertIfAbsent(
        @Param("low") low: UUID,
        @Param("high") high: UUID,
        @Param("conversationId") conversationId: UUID
    ): Int
}
