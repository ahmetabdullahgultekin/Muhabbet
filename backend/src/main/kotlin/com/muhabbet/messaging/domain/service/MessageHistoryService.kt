package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.MessagePage
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import java.time.DateTimeException
import java.time.Instant
import java.util.UUID

/**
 * The read side of messages: history pages, a conversation's media, and the background-sync
 * catch-up (#894). Every read is member-gated except [getMessagesSince], whose query is already
 * scoped to the caller's own conversations.
 */
class MessageHistoryService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val transactions: TransactionRunner
) : GetMessageHistoryUseCase {

    override fun getMessages(
        conversationId: UUID,
        userId: UUID,
        cursor: String?,
        limit: Int,
        direction: String
    ): MessagePage = transactions.inReadOnlyTransaction {
        requireMember(conversationId, userId)

        // An unparseable cursor starts over rather than failing — the same leniency the client has
        // always been given, and harmless: the page is still member-gated.
        val before = cursor?.let {
            try {
                Instant.parse(it)
            } catch (e: DateTimeException) {
                null
            }
        }

        val effectiveLimit = limit.coerceIn(1, MAX_PAGE_SIZE)
        val messages = messageRepository.findByConversationId(conversationId, before, effectiveLimit + 1)

        val hasMore = messages.size > effectiveLimit
        val page = if (hasMore) messages.take(effectiveLimit) else messages
        val nextCursor = if (hasMore) page.lastOrNull()?.serverTimestamp?.toString() else null

        MessagePage(items = page, nextCursor = nextCursor, hasMore = hasMore)
    }

    override fun getMediaMessages(conversationId: UUID, userId: UUID, limit: Int, offset: Int): List<Message> =
        transactions.inReadOnlyTransaction {
            requireMember(conversationId, userId)
            messageRepository.findMediaByConversationId(
                conversationId,
                limit.coerceIn(1, MAX_PAGE_SIZE),
                offset.coerceAtLeast(0)
            )
        }

    override fun getMessagesSince(userId: UUID, since: Instant): List<Message> =
        transactions.inReadOnlyTransaction { messageRepository.findUndeliveredForUser(userId, since) }

    private fun requireMember(conversationId: UUID, userId: UUID) {
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
