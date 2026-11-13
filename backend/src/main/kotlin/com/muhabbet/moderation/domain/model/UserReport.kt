package com.muhabbet.moderation.domain.model

import java.time.Instant
import java.util.UUID

/**
 * An abuse report (BTK Law 5651 notice-and-takedown) with a guarded lifecycle (#891).
 *
 * It used to be a flat record: any combination of target ids (including none), no check that the
 * reporter could see what they reported, no copy of the reported content — so a sender deleting
 * the message erased the evidence — and a review that could flip RESOLVED to DISMISSED and back.
 * Now a report has exactly one [target], carries an immutable [evidence] snapshot when the target
 * is a message, and leaves PENDING exactly once.
 */
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
    val createdAt: Instant = Instant.now(),
    /** What the reported message said when it was reported; null for user and conversation targets. */
    val evidence: MessageEvidence? = null
) {
    /**
     * What was reported. A message report also records the sender and conversation for the
     * reviewer's convenience, so the message id takes precedence when reading the target back.
     */
    val target: ReportTarget
        get() = when {
            reportedMessageId != null -> ReportTarget.Message(reportedMessageId)
            reportedConversationId != null -> ReportTarget.Conversation(reportedConversationId)
            else -> ReportTarget.User(reportedUserId ?: reporterId)
        }

    /** PENDING -> RESOLVED. */
    fun resolve(reviewer: UUID, now: Instant): ReportReview = close(ReportStatus.RESOLVED, reviewer, now)

    /** PENDING -> DISMISSED. */
    fun dismiss(reviewer: UUID, now: Instant): ReportReview = close(ReportStatus.DISMISSED, reviewer, now)

    private fun close(outcome: ReportStatus, reviewer: UUID, now: Instant): ReportReview =
        if (status != ReportStatus.PENDING) {
            ReportReview.AlreadyClosed(status)
        } else {
            ReportReview.Closed(copy(status = outcome, reviewedBy = reviewer, resolvedAt = now))
        }

    companion object {
        /** A new PENDING report on [target]; the message evidence, if any, is captured by the caller. */
        fun open(
            reporterId: UUID,
            target: ReportTarget,
            reason: ReportReason,
            description: String?,
            evidence: MessageEvidence?,
            now: Instant
        ): UserReport = UserReport(
            reporterId = reporterId,
            reportedUserId = when (target) {
                is ReportTarget.User -> target.userId
                is ReportTarget.Message -> evidence?.senderId
                is ReportTarget.Conversation -> null
            },
            reportedMessageId = (target as? ReportTarget.Message)?.messageId,
            reportedConversationId = when (target) {
                is ReportTarget.Conversation -> target.conversationId
                is ReportTarget.Message -> evidence?.conversationId
                is ReportTarget.User -> null
            },
            reason = reason,
            description = description,
            evidence = evidence,
            createdAt = now
        )
    }
}

/** Exactly one thing is reported. */
sealed interface ReportTarget {
    data class User(val userId: UUID) : ReportTarget
    data class Message(val messageId: UUID) : ReportTarget
    data class Conversation(val conversationId: UUID) : ReportTarget

    companion object {
        /** The single target the three optional ids name, or null when they name none or several. */
        fun of(userId: UUID?, messageId: UUID?, conversationId: UUID?): ReportTarget? {
            val named = listOfNotNull(
                userId?.let(::User),
                messageId?.let(::Message),
                conversationId?.let(::Conversation)
            )
            return named.singleOrNull()
        }
    }
}

/**
 * The reported message as it stood when it was reported. Held on the report so that the sender's
 * deletion — soft, or by erasing their account — cannot take the evidence with it.
 */
data class MessageEvidence(
    val messageId: UUID,
    val conversationId: UUID,
    val senderId: UUID,
    val contentType: String,
    val content: String,
    val mediaUrl: String?,
    val sentAt: Instant
)

sealed interface ReportReview {
    data class Closed(val report: UserReport) : ReportReview
    data class AlreadyClosed(val status: ReportStatus) : ReportReview
}

enum class ReportReason {
    SPAM,
    HARASSMENT,
    ILLEGAL_CONTENT,
    HATE_SPEECH,
    OTHER
}

/** PENDING -> RESOLVED | DISMISSED, once. `V30` folded the never-written REVIEWED into RESOLVED. */
enum class ReportStatus {
    PENDING,
    RESOLVED,
    DISMISSED
}

data class UserBlock(
    val id: UUID = UUID.randomUUID(),
    val blockerId: UUID,
    val blockedId: UUID,
    val createdAt: Instant = Instant.now()
)
