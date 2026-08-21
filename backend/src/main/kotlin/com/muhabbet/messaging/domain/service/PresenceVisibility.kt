package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import java.util.UUID

/**
 * The one statement of what a block does to presence, for every surface that shows any of it.
 *
 * **A block hides presence in both directions.** With A having blocked B: A is not shown B's online
 * dot, last seen or typing indicator, and B is not shown A's. It does not matter which of them
 * pressed Block — a block is not "make me less visible to you", it is "I am done with this person",
 * and a control that only works one way is half a control.
 *
 * Stated here, once, because the four surfaces that answer this question got four different answers
 * (#711). `GET /conversations` and `GET /users/{id}` hid the blocked person's dot from nobody but
 * themselves; `broadcastPresence` hid exactly the opposite direction, so the live feed handed back
 * what the REST call had just suppressed and the blocked person watched the blocker's dot light up
 * seconds after they connected; and typing indicators were filtered in neither direction, flowing
 * both ways through a chat where one had blocked the other. Every one of those is the same rule
 * written from memory at the call site, so the rule is no longer written at call sites.
 *
 * **The returned set is symmetric, which is why one call serves both readings.** "Whose presence
 * must be withheld from this user" and "who must not be told about this user's presence" are the
 * same set, so [hiddenFrom] answers a REST projection and a WebSocket fan-out without either
 * caller having to work out which direction it is in — the thing that went wrong.
 *
 * The two port questions stay separate below rather than being merged into one because messaging
 * has four call sites that are deliberately **one**-directional and carry a comment saying so:
 * `MessageService.isBlockedDirectSend`, `GroupService.addMembers` and the two in `CommunityService`.
 * Sending a message to someone you blocked, or adding them to a group, is your own business; being
 * shown whether they are awake is not. Those keep asking [BlockPolicyPort] directly.
 *
 * Not a use case and not annotated: it is a rule, wired as a bean in `AppConfig` like every other
 * domain service so the domain stays Spring-free.
 */
class PresenceVisibility(private val blockPolicy: BlockPolicyPort) {

    /**
     * Which of [candidateIds] must exchange no presence with [userId] — those [userId] has blocked
     * and those who have blocked [userId].
     *
     * Batched by contract: one query per direction for the whole set, never one per candidate. The
     * chat list resolves every participant on the page the moment it opens and the WebSocket
     * resolves every contact on connect, so a per-candidate question here is an N+1 on the two
     * busiest moments the app has. Empty in, empty out, and no query at all.
     */
    fun hiddenFrom(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> {
        if (candidateIds.isEmpty()) return emptySet()
        return blockPolicy.findBlockedBy(userId, candidateIds) +
            blockPolicy.findBlockedAmong(userId, candidateIds)
    }
}
