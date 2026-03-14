package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.BroadcastListJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.BroadcastListMemberId
import com.muhabbet.messaging.adapter.out.persistence.entity.BroadcastListMemberJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataBroadcastListJpaRepository : JpaRepository<BroadcastListJpaEntity, UUID> {
    fun findByOwnerId(ownerId: UUID): List<BroadcastListJpaEntity>
}

interface SpringDataBroadcastListMemberRepository : JpaRepository<BroadcastListMemberJpaEntity, BroadcastListMemberId> {
    fun findByBroadcastListId(broadcastListId: UUID): List<BroadcastListMemberJpaEntity>

    @Modifying
    @Query("DELETE FROM BroadcastListMemberJpaEntity blm WHERE blm.broadcastListId = :broadcastListId AND blm.userId = :userId")
    fun deleteByBroadcastListIdAndUserId(broadcastListId: UUID, userId: UUID)
}
