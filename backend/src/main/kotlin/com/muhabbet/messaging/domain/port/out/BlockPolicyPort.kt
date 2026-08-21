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
 * Two shapes because there are two shapes of question. The send path asks about a single pair — a
 * direct conversation has exactly one other participant. Presence asks about a whole page of
 * conversation participants at once, so it gets a batched form; resolving a 20-row chat list one
 * participant at a time would be an N+1 on the screen users open first.
 *
 * The batched form comes in **both directions**, and a caller that hides content usually needs
 * both (#687). "Who blocked me" alone hid the blocker's stories from the person they blocked and
 * left the blocker still watching theirs — half a control, and the half nobody asked for. Which
 * direction a surface needs is a question about that surface, so the port answers both and the
 * caller decides; it does not merge them, because the send path and the group-add deliberately
 * consult one direction only.
 */
interface BlockPolicyPort {

    /** True when [blockerId] has blocked [blockedId]. Directional — blocks are not mutual. */
    fun hasBlocked(blockerId: UUID, blockedId: UUID): Boolean

    /**
     * Which of [candidateIds] have blocked [userId] — i.e. whose presence must be hidden from
     * [userId]. Returns an empty set for the common case where nobody has, and costs no query when
     * [candidateIds] is empty.
     */
    fun findBlockedBy(userId: UUID, candidateIds: Collection<UUID>): Set<UUID>

    /**
     * Which of [candidateIds] [userId] has blocked — the mirror of [findBlockedBy], and the
     * question a viewer's own feed has to ask. Same batching contract: one query for the whole
     * candidate set, none at all when it is empty.
     */
    fun findBlockedAmong(userId: UUID, candidateIds: Collection<UUID>): Set<UUID>
}
