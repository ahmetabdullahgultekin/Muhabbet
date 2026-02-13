package com.muhabbet.moderation.domain.model

import java.time.Instant
import java.util.UUID

data class UserReport(
    val id: UUID = UUID.randomUUID(),
    val reporterId: UUID,
    val reportedUserId: UUID? = null,
    val reportedMessageId: UUID? = null,
    val reportedConversationId: UUID? = null,
    val reason: ReportReason,
    val description: String? = null,
    val status: ReportStatus = ReportStatus.PENDING,
    val reviewedBy: UUID? = null,
    val resolvedAt: Instant? = null,
    val createdAt: Instant = Instant.now()
)

enum class ReportReason {
    SPAM,
    HARASSMENT,
    ILLEGAL_CONTENT,
    HATE_SPEECH,
    OTHER
}

enum class ReportStatus {
    PENDING,
    REVIEWED,
    RESOLVED,
    DISMISSED
}

data class UserBlock(
    val id: UUID = UUID.randomUUID(),
    val blockerId: UUID,
    val blockedId: UUID,
    val createdAt: Instant = Instant.now()
)
