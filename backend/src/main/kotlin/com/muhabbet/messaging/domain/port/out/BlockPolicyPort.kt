package com.muhabbet.messaging.domain.port.out

import java.util.UUID

/**
 * Whether one user has blocked another.
 *
 * Blocks are owned by the moderation module. Messaging declares only the question it needs
 * answered — never the block type, never the repository — so the adapter behind this port stays
 * the one place the two modules meet. Same shape as [UserDirectoryPort] and [ReadReceiptPolicyPort],
 * and for the same reason.
 *
 * Not batched, unlike its two siblings: every caller asks about a single pair. A direct
 * conversation has exactly one other participant, and a group add is refused wholesale on the first
 * block found, so there is no page of ids to resolve and no N+1 to avoid.
 */
interface BlockPolicyPort {

    /** True when [blockerId] has blocked [blockedId]. Directional — blocks are not mutual. */
    fun hasBlocked(blockerId: UUID, blockedId: UUID): Boolean
}
