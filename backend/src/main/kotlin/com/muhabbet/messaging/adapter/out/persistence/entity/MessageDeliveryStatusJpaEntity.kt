package com.muhabbet.messaging.adapter.out.persistence.entity

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class MessageDeliveryStatusId(
    val messageId: UUID = UUID.randomUUID(),
    val userId: UUID = UUID.randomUUID()
) : Serializable

@Entity
@Table(name = "message_delivery_status")
@IdClass(MessageDeliveryStatusId::class)
class MessageDeliveryStatusJpaEntity(
    @Id
    @Column(name = "message_id")
    val messageId: UUID,

    @Id
    @Column(name = "user_id")
    val userId: UUID,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: DeliveryStatus = DeliveryStatus.SENT,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    fun toDomain(): MessageDeliveryStatus = MessageDeliveryStatus(
        messageId = messageId, userId = userId, status = status, updatedAt = updatedAt
    )

    companion object {
        fun fromDomain(d: MessageDeliveryStatus): MessageDeliveryStatusJpaEntity =
            MessageDeliveryStatusJpaEntity(
                messageId = d.messageId, userId = d.userId, status = d.status, updatedAt = d.updatedAt
            )
    }
}
