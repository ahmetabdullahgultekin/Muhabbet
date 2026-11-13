package com.muhabbet.moderation.adapter.out.persistence

import com.muhabbet.moderation.domain.model.ReportReason
import com.muhabbet.moderation.domain.model.ReportStatus
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
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 50) val reason: ReportReason,
    @Column(columnDefinition = "TEXT") val description: String? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var status: ReportStatus = ReportStatus.PENDING,
    @Column(name = "reviewed_by") var reviewedBy: UUID? = null,
    @Column(name = "resolved_at") var resolvedAt: Instant? = null,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now(),
    // The evidence snapshot (V30). Written once at report time, never updated.
    @Column(name = "evidence_message_id", updatable = false) val evidenceMessageId: UUID? = null,
    @Column(name = "evidence_conversation_id", updatable = false) val evidenceConversationId: UUID? = null,
    @Column(name = "evidence_sender_id", updatable = false) val evidenceSenderId: UUID? = null,
    @Column(name = "evidence_content_type", length = 20, updatable = false) val evidenceContentType: String? = null,
    @Column(name = "evidence_content", columnDefinition = "TEXT", updatable = false) val evidenceContent: String? = null,
    @Column(name = "evidence_media_url", columnDefinition = "TEXT", updatable = false) val evidenceMediaUrl: String? = null,
    @Column(name = "evidence_sent_at", updatable = false) val evidenceSentAt: Instant? = null
)

@Entity
@Table(name = "user_blocks")
class BlockJpaEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "blocker_id", nullable = false) val blockerId: UUID,
    @Column(name = "blocked_id", nullable = false) val blockedId: UUID,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now()
)
