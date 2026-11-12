package com.muhabbet.auth.domain.port.out

import java.util.UUID

/**
 * Whether one user has blocked another, as the auth module needs to ask it.
 *
 * Blocks are owned by the moderation module. This is a near-twin of messaging's `BlockPolicyPort`
 * and the duplication is deliberate: a module declares its own out-ports so that a change to what
 * messaging needs cannot silently change what a profile lookup returns. The same trade is already
 * accepted for the backend/shared enum pairs.
 */
interface BlockDirectoryPort {

    /** True when [blockerId] has blocked [blockedId]. Directional — blocks are not mutual. */
    fun hasBlocked(blockerId: UUID, blockedId: UUID): Boolean

    /**
     * Which of [candidateIds] are on either side of a block with [userId] — they blocked [userId],
     * or [userId] blocked them. Contact sync needs both directions (#748): the blocked user must not
     * learn the blocker's name and avatar, and the blocker has said they do not want this person
     * surfaced either. Batched by contract — one call for a whole sync, never one per candidate —
     * and an empty [candidateIds] costs no query.
     */
    fun findBlockedEitherWay(userId: UUID, candidateIds: Collection<UUID>): Set<UUID>
}
