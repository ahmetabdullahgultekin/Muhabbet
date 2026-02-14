package com.muhabbet.messaging.adapter.out.persistence.entity

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "bots")
class BotJpaEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "owner_id", nullable = false) val ownerId: UUID,
    @Column(name = "user_id", nullable = false) val userId: UUID,
    @Column(nullable = false, length = 100) var name: String,
    @Column(columnDefinition = "TEXT") var description: String? = null,
    @Column(name = "api_token", nullable = false, unique = true, length = 256) var apiToken: String,
    @Column(name = "webhook_url") var webhookUrl: String? = null,
    @Column(name = "is_active", nullable = false) var isActive: Boolean = true,
    @Column(nullable = false, columnDefinition = "JSONB") var permissions: String = """["SEND_MESSAGE","READ_MESSAGE"]""",
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)
