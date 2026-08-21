package com.muhabbet.messaging.domain.model

import java.time.Instant
import java.util.UUID

/**
 * An open offer to join a community, held by whoever has the token (#387, #416).
 *
 * A link is not a membership. Nothing in `community_members` changes when one is minted; the row is
 * written only when a person accepts, which is the point — an owner attaching a user id they could
 * guess is the defect #375 closed, and this is the path that replaces it without reopening it.
 *
 * Mirrors [GroupInviteLink] minus `requiresApproval`: communities have no admin approval queue, so
 * a link either admits the holder or has been revoked.
 */
data class CommunityInviteLink(
    val id: UUID = UUID.randomUUID(),
    val communityId: UUID,
    val inviteToken: String,
    val createdBy: UUID,
    val isActive: Boolean = true,
    val maxUses: Int? = null,
    val useCount: Int = 0,
    val expiresAt: Instant? = null,
    val createdAt: Instant = Instant.now()
) {
    /**
     * Whether this link would admit someone right now, ignoring who that someone is.
     *
     * On the model rather than in the service so that the "can this be used" question has one
     * answer: the preview screen and the accept path must agree, or the app shows a joinable
     * community and then refuses the join.
     *
     * @param now injected rather than read from the clock here so the domain stays testable without
     * a clock abstraction — the two callers both pass a single `Instant.now()` for the whole request.
     */
    fun isUsableAt(now: Instant): Boolean =
        isActive && !isExpiredAt(now) && !isExhausted()

    fun isExpiredAt(now: Instant): Boolean = expiresAt?.isBefore(now) == true

    fun isExhausted(): Boolean = maxUses?.let { useCount >= it } == true
}
