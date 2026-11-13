package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.GetMessageInfoUseCase
import com.muhabbet.messaging.domain.port.`in`.MessageInfo
import com.muhabbet.messaging.domain.port.`in`.MessageRecipient
import com.muhabbet.messaging.domain.port.`in`.ResolveDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.`in`.UpdateDeliveryStatusUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * Delivery receipts: storing them, and every place one is published (#894).
 *
 * The three publish paths — the live WebSocket update ([updateStatus]), the history aggregate
 * ([resolveDeliveryStatuses]) and the per-recipient "Info" list ([getMessageInfo]) — live in one
 * class on purpose. Each must apply the read-receipt downgrade, and #620 was the one that had been
 * missed; with all three side by side a fourth cannot be added without the other three in view.
 */
class DeliveryReceiptService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val messageBroadcaster: MessageBroadcaster,
    private val userDirectory: UserDirectoryPort,
    private val readReceiptPolicy: ReadReceiptPolicyPort,
    private val transactions: TransactionRunner
) : UpdateDeliveryStatusUseCase, ResolveDeliveryStatusUseCase, GetMessageInfoUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * This runs on every delivery and every read receipt, roughly once per message delivered. The
     * fan-out is outside the transaction for the same reason the send path's is (#491, #669): it
     * would otherwise hold a pool connection across the status broadcast.
     *
     * The trade the move accepts: a broadcast that fails after the commit means a stored status
     * nobody was pushed, so the sender keeps one tick until their client next loads history, which
     * resolves the status from the same rows. Handing a client a receipt for a row a rollback then
     * removed is the worse of the two, and that is the one this removes.
     */
    override fun updateStatus(messageId: UUID, userId: UUID, status: DeliveryStatus) {
        val outcome = transactions.inTransaction { persistStatus(messageId, userId, status) }

        val message = outcome.message ?: return
        messageBroadcaster.broadcastStatusUpdate(
            messageId,
            message.conversationId,
            userId,
            message.senderId,
            outcome.published
        )

        log.debug(
            "Delivery status updated: msg={}, user={}, stored={}, published={}",
            messageId,
            userId,
            status,
            outcome.published
        )
    }

    /**
     * What the status transaction produced. A null [message] means the id did not resolve, so
     * there is nobody to tell.
     */
    private data class StatusOutcome(val message: Message?, val published: DeliveryStatus)

    private fun persistStatus(messageId: UUID, userId: UUID, status: DeliveryStatus): StatusOutcome {
        messageRepository.updateDeliveryStatus(messageId, userId, status)

        val message = messageRepository.findById(messageId) ?: return StatusOutcome(null, status)
        // A reader who has turned read receipts off still gets their own row stored as READ — that
        // row is what clears their unread badge — but the sender must not be told. We downgrade what
        // is *published*, never what is stored, because one column serves both concerns.
        // Only READ can be downgraded, so DELIVERED acks skip the lookup entirely — this runs on
        // every ack from every client.
        val published = if (status == DeliveryStatus.READ) {
            publishableStatus(userId, status, readReceiptPolicy.findReadReceiptsDisabled(listOf(userId)))
        } else {
            status
        }
        return StatusOutcome(message, published)
    }

    override fun markConversationRead(conversationId: UUID, userId: UUID) {
        transactions.inTransaction {
            messageRepository.markConversationRead(conversationId, userId)
            conversationRepository.updateLastReadAt(conversationId, userId, Instant.now())
        }
        log.debug("Conversation marked as read: conv={}, user={}", conversationId, userId)
    }

    override fun resolveDeliveryStatuses(
        messages: List<Message>,
        requestingUserId: UUID
    ): Map<UUID, DeliveryStatus> {
        if (messages.isEmpty()) return emptyMap()
        return transactions.inReadOnlyTransaction { resolve(messages, requestingUserId) }
    }

    private fun resolve(messages: List<Message>, requestingUserId: UUID): Map<UUID, DeliveryStatus> {
        val allStatuses = messageRepository.getDeliveryStatuses(messages.map { it.id })
        val statusesByMessageId = allStatuses.groupBy { it.messageId }
        // One batched lookup for the whole page, and only for rows that are actually READ — the
        // port short-circuits on an empty collection, so a page nobody has read costs no query.
        // Without this the live WS downgrade in [updateStatus] would be undone the moment the
        // sender scrolled or reopened the chat, because the stored row is still READ.
        val receiptsDisabled = readReceiptPolicy.findReadReceiptsDisabled(
            allStatuses.filter { it.status == DeliveryStatus.READ }.map { it.userId }
        )

        return messages.associate { message ->
            val statuses = statusesByMessageId[message.id] ?: emptyList()
            val resolved = if (message.senderId == requestingUserId) {
                // Sender perspective: aggregate across all recipients.
                aggregate(statuses.map { publishableStatus(it.userId, it.status, receiptsDisabled) })
            } else {
                // Recipient perspective: their own status row.
                statuses.firstOrNull { it.userId == requestingUserId }?.status ?: DeliveryStatus.SENT
            }
            message.id to resolved
        }
    }

    /** All READ → READ, any DELIVERED/READ → DELIVERED, else SENT. */
    private fun aggregate(visible: List<DeliveryStatus>): DeliveryStatus = when {
        visible.isEmpty() -> DeliveryStatus.SENT
        visible.all { it == DeliveryStatus.READ } -> DeliveryStatus.READ
        visible.any { it == DeliveryStatus.DELIVERED || it == DeliveryStatus.READ } -> DeliveryStatus.DELIVERED
        else -> DeliveryStatus.SENT
    }

    override fun getMessageInfo(messageId: UUID, requesterId: UUID): MessageInfo = transactions.inReadOnlyTransaction {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        // Authorize FIRST: only members of the conversation may read message info
        // (content, senderId, recipient list). Closes the getMessageInfo IDOR.
        conversationRepository.findMember(message.conversationId, requesterId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)

        // The sender is not a recipient of their own message.
        val statuses = messageRepository.getDeliveryStatuses(listOf(messageId))
            .filter { it.userId != message.senderId }

        // One batched lookup for the whole recipient list — resolving names one at a time was an N+1.
        val displayInfo = userDirectory.findDisplayInfo(statuses.map { it.userId })

        // The third publish path, and the one that was missed. updateStatus downgrades the live WS
        // broadcast and resolveDeliveryStatuses downgrades the history aggregate, but "Info" names
        // each recipient individually and printed the stored row verbatim — so a reader who had
        // turned read receipts off still appeared under "Read by", with the time they opened it.
        // Same batched, READ-only lookup as the aggregate above (#620).
        val receiptsDisabled = readReceiptPolicy.findReadReceiptsDisabled(
            statuses.filter { it.status == DeliveryStatus.READ && it.userId != requesterId }
                .map { it.userId }
        )

        MessageInfo(
            message = message,
            recipients = statuses.map { status ->
                val user = displayInfo[status.userId]
                // Your own state is always truthful to you — the same rule resolveDeliveryStatuses
                // applies when the requester is a recipient. The setting governs what others are
                // told about you, not what you are told about yourself.
                val published =
                    if (status.userId == requesterId) status.status
                    else publishableStatus(status.userId, status.status, receiptsDisabled)
                MessageRecipient(
                    userId = status.userId,
                    displayName = user?.displayName,
                    avatarUrl = user?.avatarUrl,
                    status = published,
                    updatedAt = if (published == status.status) status.updatedAt else null
                )
            }
        )
    }
}
