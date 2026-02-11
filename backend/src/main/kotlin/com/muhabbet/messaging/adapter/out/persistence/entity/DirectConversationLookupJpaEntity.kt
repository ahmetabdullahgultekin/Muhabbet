package com.muhabbet.messaging.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

data class DirectConversationLookupId(
    val userIdLow: UUID = UUID.randomUUID(),
    val userIdHigh: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "direct_conversation_lookup")
@IdClass(DirectConversationLookupId::class)
class DirectConversationLookupJpaEntity(
    @Id
    @Column(name = "user_id_low")
    val userIdLow: UUID,

    @Id
    @Column(name = "user_id_high")
    val userIdHigh: UUID,

    @Column(name = "conversation_id", nullable = false)
    val conversationId: UUID
)
