package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Receipt
import com.muhabbet.messaging.domain.model.ReceiptAdvance
import com.muhabbet.messaging.domain.model.senderView
import com.muhabbet.messaging.domain.port.`in`.RecordReceiptUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/**
 * The delivery/read lifecycle of a message, as one use case (#870, #871).
 *
 * Split out of `MessageService`, which carried it as two loosely related methods the WebSocket
 * handler stitched together. Three things are different now, and each is the fix for a defect:
 *
 * 1. **The store decides whether a receipt moved anything.** Both writes are conditional updates
 *    whose `WHERE` is [DeliveryStatus.advancesFrom], and they return only the rows they advanced.
 *    The old code published the status it was *given*, whatever had been stored — so a slow REST
 *    DELIVERED landing after READ turned the sender's blue ticks grey again, live.
 * 2. **Every message a READ receipt turns read is published**, to its own sender. The old READ path
 *    bulk-updated the whole conversation and then announced one message id, so in a group, or after
 *    any backlog, every other sender's ticks stayed grey until they reloaded.
 * 3. **What is published is the sender's aggregate tick**, not one recipient's state. A group
 *    message's tick is the minimum over its recipients ([DeliveryStatus.aggregate]), computed by
 *    [senderView] — the same function the REST history path uses — so the tick a sender sees live
 *    is the tick they see after a reload. Previously the first member to open the chat turned the
 *    sender's tick blue for a group of ten.
 *
 * **Transactions.** The write runs in its own short transaction; the aggregate is read and the
 * fan-out happens after it commits (#669 — no pool connection held across a broadcast, no receipt
 * for a row a rollback removed). Reading the aggregate *after* commit is also what makes concurrent
 * receipts converge: of two group members' receipts committing at once, whichever commits second
 * reads both rows and publishes the final state. Read inside the transaction, both could read the
 * other's row as it was before and both publish the stale aggregate. Two publishes for one message
 * can still reach the sender in either order — no transport here orders them — which is why the
 * client merges them monotonically rather than assigning.
 */
open class DeliveryReceiptService(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val messageBroadcaster: MessageBroadcaster,
    private val readReceiptPolicy: ReadReceiptPolicyPort,
    private val transactions: TransactionRunner
) : RecordReceiptUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun record(receipt: Receipt) {
        val advanced = transactions.inTransaction { persist(receipt) }
        if (advanced.isEmpty()) {
            log.debug("Receipt changed nothing: {}", receipt)
            return
        }
        publish(advanced, receipt.readerId)
    }

    private fun persist(receipt: Receipt): List<ReceiptAdvance> = when (receipt) {
        is Receipt.Delivered -> listOfNotNull(
            messageRepository.advanceDeliveryStatus(receipt.messageId, receipt.readerId, DeliveryStatus.DELIVERED)
        )
        is Receipt.Read -> {
            val advanced = messageRepository.markReadUpTo(receipt.conversationId, receipt.readerId, receipt.upToMessageId)
            // The reader's own last-read marker moves whether or not a row did: a repeated READ for
            // a chat already read is still "I have looked at this conversation now".
            conversationRepository.updateLastReadAt(receipt.conversationId, receipt.readerId, Instant.now())
            advanced
        }
    }

    /**
     * One status event per advanced message, to that message's sender, carrying the sender's
     * aggregate. A constant number of queries however many messages advanced: one for the distinct
     * states per message, and — only for messages every recipient has now read, where the privacy
     * downgrade can change the answer — one for those rows and one policy lookup.
     */
    private fun publish(advanced: List<ReceiptAdvance>, readerId: UUID) {
        val ticks = sendersTicks(advanced.map { it.messageId })
        advanced.forEach { row ->
            val tick = ticks[row.messageId] ?: return@forEach
            messageBroadcaster.broadcastStatusUpdate(row.messageId, row.conversationId, readerId, row.senderId, tick)
        }
        log.debug("Receipt from {} advanced {} message(s)", readerId, advanced.size)
    }

    private fun sendersTicks(messageIds: List<UUID>): Map<UUID, DeliveryStatus> {
        val statusSets = messageRepository.findDeliveryStatusSets(messageIds)
        val raw = statusSets.mapValues { (_, statuses) -> DeliveryStatus.aggregate(statuses) }

        // A reader with receipts off is published as DELIVERED, so only a message whose raw
        // aggregate is READ — everyone has read it — can come out differently once the policy is
        // applied. For any lower aggregate some recipient is below READ already, and downgrading a
        // READ to DELIVERED cannot lower the minimum past it.
        val allRead = raw.filterValues { it == DeliveryStatus.READ }.keys
        if (allRead.isEmpty()) return raw

        val rows = messageRepository.getDeliveryStatuses(allRead.toList())
        val receiptsDisabled = readReceiptPolicy.findReadReceiptsDisabled(rows.map { it.userId }.toSet())
        val resolved = rows.groupBy { it.messageId }.mapValues { (_, perRecipient) -> senderView(perRecipient, receiptsDisabled) }
        return raw + resolved
    }
}
