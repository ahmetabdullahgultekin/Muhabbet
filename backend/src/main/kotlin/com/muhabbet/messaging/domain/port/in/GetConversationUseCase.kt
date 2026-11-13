package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

/**
 * Read one conversation by id, as the member asking for it sees it (#800).
 *
 * The list endpoint is paged, so a client holding only an id had to walk pages until it happened to
 * find it — and the screens that did so only ever looked at page one. A conversation past the
 * twentieth chat then opened with no members, no roles, no disappearing timer and no
 * announcement-only flag. This is the read-by-id that question always needed.
 */
interface GetConversationUseCase {
    /**
     * @throws com.muhabbet.shared.exception.BusinessException `CONV_NOT_FOUND` both when the
     *   conversation does not exist and when [userId] is not a member of it. The two are answered
     *   identically on purpose: a caller who is not in a conversation has no business learning that
     *   it exists.
     */
    fun getConversation(userId: UUID, conversationId: UUID): ConversationDetail
}

data class ConversationDetail(
    val conversation: Conversation,
    /** Every member with their real role — the list endpoint reports everyone as MEMBER. */
    val members: List<ConversationMember>,
    /** The caller's own membership row: pin, mute, archive and lock are per-user flags. */
    val viewer: ConversationMember,
    val lastMessage: Message?,
    val unreadCount: Int
)
