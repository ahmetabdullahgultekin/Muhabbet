package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.BroadcastListMember
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

data class BroadcastListMemberId(
    val broadcastListId: UUID = UUID.randomUUID(),
    val userId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "broadcast_list_members")
@IdClass(BroadcastListMemberId::class)
class BroadcastListMemberJpaEntity(
    @Id
    @Column(name = "broadcast_list_id")
    val broadcastListId: UUID,

    @Id
    @Column(name = "user_id")
    val userId: UUID
) {
    fun toDomain(): BroadcastListMember = BroadcastListMember(
        broadcastListId = broadcastListId, userId = userId
    )

    companion object {
        fun fromDomain(blm: BroadcastListMember): BroadcastListMemberJpaEntity = BroadcastListMemberJpaEntity(
            broadcastListId = blm.broadcastListId, userId = blm.userId
        )
    }
}
