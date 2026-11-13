package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.HistoryWindow
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import java.util.UUID

/**
 * The one read-authorization check for message history (#874): membership *and* the window that
 * membership opens.
 *
 * Every service that hands a message to a user used to repeat `findMember(...) ?: throw` and stop
 * there. That answered "is this person in the conversation now?" and nothing about which part of it
 * they may read. Keeping both halves here means a new read path picks up the window by calling the
 * same function it would have called for membership anyway.
 *
 * A plain class built by each service from the [ConversationRepository] it already holds, rather
 * than a bean: it has no state and no dependency of its own, so wiring it would add a constructor
 * argument to every service and every test without adding anything a reader needs.
 */
class HistoryAccess(private val conversationRepository: ConversationRepository) {

    /**
     * The window [userId] reads [conversationId] through. Throws `MSG_NOT_MEMBER` for a non-member.
     */
    fun windowFor(conversationId: UUID, userId: UUID): HistoryWindow {
        val member = conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.MSG_CONVERSATION_NOT_FOUND)
        return HistoryWindow.of(conversation.type, member)
    }

    /**
     * Authorizes a read of one message addressed by id: `MSG_NOT_MEMBER` for a non-member, and
     * [notVisible] — not-found by default — for a message from before this membership began.
     *
     * Not-found rather than forbidden on purpose. A message outside the window does not exist as far
     * as this member is concerned, and a distinct error would let anyone holding an id learn that
     * something was said in the group before they were let in.
     *
     * A message stamped at or after the member's `joinedAt` is visible under every policy, so the
     * conversation is only looked up when the answer could actually depend on its type.
     */
    fun requireVisible(message: Message, userId: UUID, notVisible: ErrorCode = ErrorCode.MSG_NOT_FOUND) {
        val member = conversationRepository.findMember(message.conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
        if (!message.serverTimestamp.isBefore(member.joinedAt)) return
        // A conversation that no longer resolves admits nothing.
        val type = conversationRepository.findById(message.conversationId)?.type
        if (type == null || !HistoryWindow.of(type, member).admits(message)) {
            throw BusinessException(notVisible)
        }
    }
}
