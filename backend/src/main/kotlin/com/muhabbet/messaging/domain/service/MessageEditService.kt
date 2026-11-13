package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.transaction.TransactionRunner
import com.muhabbet.shared.validation.ValidationRules
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Changing a message after it was sent: deleting it, and editing it inside the edit window (#894).
 *
 * Both persist inside a transaction and fan out after it commits (#669), and both apply the same
 * ownership guard. Edit also applies the direct-message block, because `updateContent` is the second
 * way message content reaches a recipient.
 */
class MessageEditService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val messageBroadcaster: MessageBroadcaster,
    blockPolicy: BlockPolicyPort,
    private val transactions: TransactionRunner
) : ManageMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val directSendBlock = DirectSendBlockPolicy(blockPolicy)

    /**
     * The fan-out is after the commit — #669, the same move the send path made in #491. A delete
     * announces itself to every member of the conversation, so a write that held a pool connection
     * across that fan-out held it for as many blocking socket writes as the group has people.
     *
     * The trade: if the broadcast fails after the commit, the row is deleted and an open chat goes
     * on rendering it until the client reloads history, which no longer returns it. The reverse —
     * telling everyone a message is gone and then rolling the delete back — leaves them unable to
     * see a message that still exists, with nothing to correct it.
     */
    override fun deleteMessage(messageId: UUID, requesterId: UUID) {
        val outcome = transactions.inTransaction { persistDelete(messageId, requesterId) }

        messageBroadcaster.broadcastToUsers(
            outcome.recipients,
            WsMessage.MessageDeleted(
                messageId = messageId.toString(),
                conversationId = outcome.conversationId.toString(),
                deletedBy = requesterId.toString(),
                timestamp = System.currentTimeMillis()
            )
        )

        log.info("Message {} soft-deleted by {}", messageId, requesterId)
    }

    /** What the delete transaction produced: who to tell, and which conversation to name. */
    private data class DeleteOutcome(val conversationId: UUID, val recipients: List<UUID>)

    private fun persistDelete(messageId: UUID, requesterId: UUID): DeleteOutcome {
        val message = requireOwnLiveMessage(messageId, requesterId)

        messageRepository.softDelete(messageId)

        val members = conversationRepository.findMembersByConversationId(message.conversationId)
        return DeleteOutcome(message.conversationId, members.map { it.userId })
    }

    /**
     * The fan-out is after the commit — #669. `MessageEdited` goes to every member, so this had the
     * same connection-per-fan-out shape as [deleteMessage].
     *
     * The trade: a broadcast that fails after the commit leaves an open chat showing the old text
     * until it reloads, where it will read the new one. The old shape could push the new text and
     * then roll the write back, leaving readers with a version of the message that exists nowhere.
     */
    override fun editMessage(messageId: UUID, requesterId: UUID, newContent: String): Message {
        val outcome = transactions.inTransaction { persistEdit(messageId, requesterId, newContent) }

        // A null recipient list is the block case: nothing was written, so nothing goes out.
        outcome.recipients?.let { recipients ->
            messageBroadcaster.broadcastToUsers(
                recipients,
                WsMessage.MessageEdited(
                    messageId = messageId.toString(),
                    conversationId = outcome.message.conversationId.toString(),
                    editedBy = requesterId.toString(),
                    newContent = newContent,
                    editedAt = outcome.editedAt.toEpochMilli()
                )
            )
            log.info("Message {} edited by {}", messageId, requesterId)
        }

        return outcome.message
    }

    /**
     * What the edit transaction produced. A null [recipients] means the edit was dropped because
     * the recipient has blocked the sender — nothing was written and nothing may go out — as
     * distinct from an empty list.
     */
    private data class EditOutcome(
        val message: Message,
        val editedAt: Instant,
        val recipients: List<UUID>?
    )

    private fun persistEdit(messageId: UUID, requesterId: UUID, newContent: String): EditOutcome {
        val message = requireOwnLiveMessage(messageId, requesterId)

        // Through the shared rule rather than a local Duration, so the answer the app computed
        // before offering "Düzenle" and the answer the server computes here cannot disagree.
        val withinWindow = ValidationRules.isWithinEditWindow(
            sentAtEpochMillis = message.serverTimestamp.toEpochMilli(),
            nowEpochMillis = Instant.now().toEpochMilli()
        )
        if (!withinWindow) {
            throw BusinessException(ErrorCode.MSG_EDIT_WINDOW_EXPIRED)
        }

        requireValidMessageContent(newContent)

        val editedAt = Instant.now()

        // `updateContent` is the *second* way message content reaches a recipient, and guarding only
        // the send left it open: for the edit window after their last pre-block message, a blocked
        // sender could rewrite it to anything and MessageEdited would push the new text into the
        // blocker's open chat — repeatedly, on the same message. Same silence as the send path: the
        // caller is told it worked, nothing is written and nothing is broadcast.
        val conversation = conversationRepository.findById(message.conversationId)
        val members = conversationRepository.findMembersByConversationId(message.conversationId)
        val edited = message.copy(content = newContent, editedAt = editedAt)

        if (directSendBlock.isBlocked(conversation, members, requesterId)) {
            log.info("Message edit dropped, recipient has blocked the sender: id={}", messageId)
            return EditOutcome(edited, editedAt, recipients = null)
        }

        messageRepository.updateContent(messageId, newContent, editedAt)

        return EditOutcome(edited, editedAt, members.map { it.userId })
    }

    /**
     * The message [requesterId] is allowed to change, or the reason they are not.
     *
     * Deleting and editing ask the same questions in the same order. One helper keeps the
     * precedence deliberate: a message that does not exist and a message that is not yours are
     * answered differently, and "already deleted" is only reachable once ownership is established.
     */
    private fun requireOwnLiveMessage(messageId: UUID, requesterId: UUID): Message {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        rejectionFor(message, requesterId)?.let { throw BusinessException(it) }
        return message
    }

    /** Why [requesterId] may not change [message], or null if they may. */
    private fun rejectionFor(message: Message, requesterId: UUID): ErrorCode? = when {
        message.senderId != requesterId -> ErrorCode.MSG_NOT_SENDER
        message.isDeleted -> ErrorCode.MSG_ALREADY_DELETED
        else -> null
    }
}
