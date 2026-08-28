package com.muhabbet.messaging.adapter.`in`.websocket

import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Who must not be told what someone is doing right now.
 *
 * Extracted from [ChatWebSocketHandler] with #711, which is the issue that made it worth naming.
 * The live presence frame and the typing frame are two sockets carrying the same fact — "this
 * person is here" — and they had drifted: the online broadcast filtered one direction of a block,
 * `GET /conversations` filtered the *other*, and typing filtered neither. Each channel closed the
 * direction another left open, so the pair leaked both ways at once. Keeping both questions in one
 * class is what stops them drifting again.
 */
@Component
class PresenceVisibility(
    private val blockPolicy: BlockPolicyPort,
    private val conversationRepository: ConversationRepository
) {

    /**
     * Which of [candidateIds] must neither see [userId]'s presence nor be seen by them — the union
     * of "who has blocked me" and "who I have blocked".
     *
     * Both directions, and the same union `StatusService` applies to the Updates tab and
     * `ConversationController` to the chat list. A block is not a request to be less visible; it is
     * a request to be done with someone, and presence is the channel that makes "done" visible.
     *
     * Two batched queries for the whole contact set, and none at all when it is empty — this runs
     * on every connect and disconnect, so a question per contact would be an N+1 on the busiest
     * moment the socket has.
     */
    fun hiddenFromPresenceOf(userId: UUID, candidateIds: Collection<UUID>): Set<UUID> =
        blockPolicy.findBlockedBy(userId, candidateIds) + blockPolicy.findBlockedAmong(userId, candidateIds)

    /**
     * Which of [recipientIds] must not be told that [userId] is typing in [conversationId].
     *
     * This frame had no block check in either direction, so "yazıyor…" — the liveliest presence
     * signal the app has — kept flowing both ways through a blocked one-to-one chat.
     *
     * Symmetric, like the presence frame, and for a sharper reason: a chat where one side sees
     * typing and the other does not announces the block and the moment it happened.
     *
     * **Direct conversations only**, which is the deliberate limit rather than an oversight. A
     * block does not stop a group message either — `MessageService` drops only direct sends — so
     * filtering group typing would make the two disagree: the blocked member's messages would
     * arrive while their typing did not, which reads as a bug and half-announces the block to the
     * room.
     *
     * The pair question is asked before the conversation is loaded, so the ordinary case — no
     * block between these two — costs two indexed lookups and no extra row. Typing arrives on every
     * keystroke burst, and a third query on that path is worth paying only when there is something
     * to hide.
     */
    fun hiddenFromTypingIn(conversationId: UUID, userId: UUID, recipientIds: List<UUID>): Set<UUID> {
        val other = recipientIds.singleOrNull() ?: return emptySet()
        val blocked = blockPolicy.hasBlocked(userId, other) || blockPolicy.hasBlocked(other, userId)
        if (!blocked) return emptySet()

        val isDirect = conversationRepository.findById(conversationId)?.type == ConversationType.DIRECT
        return if (isDirect) setOf(other) else emptySet()
    }
}
