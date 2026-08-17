package com.muhabbet.auth.domain.port.out

import java.util.UUID

/**
 * Whether one user has blocked another, as the auth module needs to ask it.
 *
 * Blocks are owned by the moderation module. This is a near-twin of messaging's `BlockPolicyPort`
 * and the duplication is deliberate: a module declares its own out-ports so that a change to what
 * messaging needs cannot silently change what a profile lookup returns. The same trade is already
 * accepted for the backend/shared enum pairs. Both ports are a single method over one adapter, so
 * the cost is a file, not a second implementation.
 */
interface BlockDirectoryPort {

    /** True when [blockerId] has blocked [blockedId]. Directional — blocks are not mutual. */
    fun hasBlocked(blockerId: UUID, blockedId: UUID): Boolean
}
