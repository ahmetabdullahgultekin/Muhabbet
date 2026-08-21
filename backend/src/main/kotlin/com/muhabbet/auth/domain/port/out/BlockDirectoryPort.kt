package com.muhabbet.auth.domain.port.out

import java.util.UUID

/**
 * Whether a block stands between two users, as the auth module needs to ask it.
 *
 * Blocks are owned by the moderation module. This is a near-twin of messaging's `BlockPolicyPort`
 * and the duplication is deliberate: a module declares its own out-ports so that a change to what
 * messaging needs cannot silently change what a profile lookup returns. The same trade is already
 * accepted for the backend/shared enum pairs. Both ports are a single adapter, so the cost is a
 * file, not a second implementation.
 *
 * **The question is symmetric, and there is deliberately no directional one here.** A block hides
 * presence, last seen and about in *both* directions — see `PresenceVisibility` in messaging, which
 * states that rule and the reasoning for it once. This port used to expose `hasBlocked`, and the
 * profile lookup asked it one way round, so someone who blocked a harasser went on being shown that
 * harasser's dot and last seen (#711). A caller that cannot name a direction cannot pick the wrong
 * one, which is the only guard that survives the next person to touch this file.
 */
interface BlockDirectoryPort {

    /**
     * True when either user has blocked the other. Blocks themselves are directional; what a
     * profile does about them is not.
     */
    fun blockExistsBetween(userId: UUID, otherUserId: UUID): Boolean
}
