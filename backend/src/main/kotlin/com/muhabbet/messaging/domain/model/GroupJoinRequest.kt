package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

/**
 * PENDING is the only state a request leaves. APPROVED and REJECTED are terminal: an admin's
 * rejection cannot be undone by another admin clicking Approve on a stale screen (#888).
 */
enum class JoinRequestStatus {
    PENDING, APPROVED, REJECTED
}

data class GroupJoinRequest(
    val id: UUID = UUID.randomUUID(),
    val conversationId: UUID,
    val userId: UUID,
    val inviteLinkId: UUID? = null,
    val status: JoinRequestStatus = JoinRequestStatus.PENDING,
    val reviewedBy: UUID? = null,
    val createdAt: Instant = Instant.now(),
    val reviewedAt: Instant? = null
) {
    /** PENDING -> APPROVED by [by] at [now]; any other starting state is refused. */
    fun approve(by: UUID, now: Instant): JoinRequestReview = review(JoinRequestStatus.APPROVED, by, now)

    /** PENDING -> REJECTED by [by] at [now]; any other starting state is refused. */
    fun reject(by: UUID, now: Instant): JoinRequestReview = review(JoinRequestStatus.REJECTED, by, now)

    private fun review(outcome: JoinRequestStatus, by: UUID, now: Instant): JoinRequestReview =
        if (status != JoinRequestStatus.PENDING) {
            JoinRequestReview.AlreadyReviewed(status)
        } else {
            JoinRequestReview.Reviewed(copy(status = outcome, reviewedBy = by, reviewedAt = now))
        }
}

/** The result of a review transition. The persistence write must still be conditional on PENDING. */
sealed interface JoinRequestReview {
    data class Reviewed(val request: GroupJoinRequest) : JoinRequestReview
    data class AlreadyReviewed(val status: JoinRequestStatus) : JoinRequestReview
}
