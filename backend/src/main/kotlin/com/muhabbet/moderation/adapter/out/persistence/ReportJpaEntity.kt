package com.muhabbet.moderation.adapter.out.persistence

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_reports")
class ReportJpaEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "reporter_id", nullable = false) val reporterId: UUID,
    @Column(name = "reported_user_id") val reportedUserId: UUID? = null,
    @Column(name = "reported_message_id") val reportedMessageId: UUID? = null,
    @Column(name = "reported_conversation_id") val reportedConversationId: UUID? = null,
    @Column(nullable = false, length = 50) val reason: String,
    @Column(columnDefinition = "TEXT") val description: String? = null,
    @Column(nullable = false, length = 20) var status: String = "PENDING",
    @Column(name = "reviewed_by") var reviewedBy: UUID? = null,
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now()
)

@Entity
@Table(name = "user_blocks")
class BlockJpaEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "blocker_id", nullable = false) val blockerId: UUID,
    @Column(name = "blocked_id", nullable = false) val blockedId: UUID,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now()
)
