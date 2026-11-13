package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.StarDecision
import com.muhabbet.messaging.domain.model.StarPolicy
import com.muhabbet.messaging.domain.model.StarredCursor
import com.muhabbet.messaging.domain.model.StarredMessagePage
import com.muhabbet.messaging.domain.port.`in`.GetStarredMessagesUseCase
import com.muhabbet.messaging.domain.port.`in`.StarMessageUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Starred messages, with the authorization the feature was built without (#906).
 *
 * Until then the controller called the out-port directly: any authenticated user could star any
 * message id and read its current content back through `GET /starred` — a former group member,
 * edits made after they left, view-once captions, scheduled messages not yet due.
 *
 * The rule is enforced twice, on purpose. [star] refuses what the caller could not read, which keeps
 * the bookmark table honest; [getStarred] re-applies membership and visibility in the query itself,
 * because a bookmark that was legitimate when written stops being so when the user leaves the
 * conversation or the message changes state. The read side never trusts what the write side stored.
 */
open class StarredMessageService(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val starredMessageRepository: StarredMessageRepository
) : StarMessageUseCase, GetStarredMessagesUseCase {

    companion object {
        const val MAX_PAGE_SIZE = 100
    }

    @Transactional
    override fun star(userId: UUID, messageId: UUID) {
        val message = messageRepository.findById(messageId)
        val isMember = message
            ?.let { conversationRepository.findMember(it.conversationId, userId) != null }
            ?: false

        when (StarPolicy.decide(message, isMember, userId)) {
            StarDecision.Allowed -> starredMessageRepository.star(userId, messageId)
            StarDecision.NotVisible -> throw BusinessException(ErrorCode.MSG_NOT_FOUND)
            StarDecision.AlreadyDeleted -> throw BusinessException(ErrorCode.MSG_ALREADY_DELETED)
            StarDecision.NotStarrable -> throw BusinessException(ErrorCode.MSG_NOT_STARRABLE)
        }
    }

    @Transactional
    override fun unstar(userId: UUID, messageId: UUID) {
        starredMessageRepository.unstar(userId, messageId)
    }

    @Transactional(readOnly = true)
    override fun getStarred(userId: UUID, cursor: String?, limit: Int): StarredMessagePage {
        if (limit !in 1..MAX_PAGE_SIZE) throw BusinessException(ErrorCode.VALIDATION_ERROR)
        val before = cursor?.let {
            StarredCursor.parse(it) ?: throw BusinessException(ErrorCode.MSG_INVALID_CURSOR)
        }

        // One row past the page tells us whether there is another page without a count query.
        val rows = starredMessageRepository.findVisibleStarred(userId, before, limit + 1)
        val hasMore = rows.size > limit
        val page = if (hasMore) rows.take(limit) else rows
        val nextCursor = if (hasMore) page.lastOrNull()?.let { StarredCursor(it.starredAt, it.starId) } else null

        return StarredMessagePage(items = page, nextCursor = nextCursor, hasMore = hasMore)
    }
}
