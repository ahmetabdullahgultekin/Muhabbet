package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.DeliverScheduledMessagesUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * The scheduled-message pipeline, out of the send service where it used to hide (#894).
 *
 * A message scheduled for later is written by the send path with `isScheduled = true` and no
 * delivery rows; this service is what turns it into a delivered message when it comes due.
 */
class ScheduledDeliveryService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val messageBroadcaster: MessageBroadcaster,
    blockPolicy: BlockPolicyPort,
    private val transactions: TransactionRunner
) : DeliverScheduledMessagesUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val directSendBlock = DirectSendBlockPolicy(blockPolicy)

    /**
     * `markAsDelivered` and `softDelete` are `@Modifying` queries, and Hibernate refuses those
     * outside a transaction — so each message gets one of its own, opened below. There is
     * deliberately no transaction around the whole run (#560).
     */
    override fun deliverScheduledMessages(): Int {
        val now = Instant.now()
        val scheduledMessages = messageRepository.findScheduledMessagesReadyToSend(now, SCHEDULED_BATCH_LIMIT)
        var delivered = 0

        for (message in scheduledMessages) {
            // One transaction per message, and the try/catch that makes it worth having. Before
            // this, a single message that could not be delivered took the whole run down with it:
            // the batch shared one transaction, so its rollback also undid `markAsDelivered` for
            // every message already handled, leaving them due again. The next run a minute later
            // selected the same batch in the same `scheduled_at ASC` order and hit the same message.
            // Nothing recovered from that, and the only symptom was one log line a minute (#560).
            //
            // The try/catch alone would NOT have fixed it, which is the part worth remembering.
            // Spring Data's write methods carry their own `@Transactional`; joining a shared
            // transaction and then throwing marks it rollback-only, so the catch swallows an
            // exception that has already doomed the commit and the batch dies at commit time with
            // UnexpectedRollbackException — reporting success all the way. Isolation has to come
            // from a real transaction boundary per message, which is what `inTransaction` is.
            //
            // And nothing may hold a transaction around this loop, for the same reason:
            // TransactionRunner uses the default REQUIRED propagation, so with an outer transaction
            // in place every per-message call would simply join it and the isolation would be
            // imaginary.
            @Suppress("TooGenericExceptionCaught") // one poison message must not stop the run
            try {
                val recipients = transactions.inTransaction { deliverOneScheduled(message) }

                // Outside the transaction, as on the immediate send path (#491): the fan-out is
                // blocking WebSocket writes and a push, and holding a pool connection across them
                // is what capped the instance at twenty concurrent sends.
                if (recipients != null) {
                    messageBroadcaster.broadcastMessage(message, recipients)
                    delivered++
                    log.info("Scheduled message delivered: id={}, conv={}", message.id, message.conversationId)
                }
            } catch (e: Exception) {
                // Named, so a poison message can be identified and acted on rather than producing
                // an identical line every minute.
                log.error(
                    "Scheduled message could not be delivered, skipping it: id={}, conv={}",
                    message.id,
                    message.conversationId,
                    e
                )
            }
        }

        return delivered
    }

    /**
     * The persistence half of delivering one scheduled message, run in its own transaction. Returns
     * the recipients to fan out to, or `null` when the message was dropped for a block — as
     * distinct from an empty list, which is a conversation the sender is alone in.
     */
    private fun deliverOneScheduled(message: Message): List<ConversationMember>? {
        messageRepository.markAsDelivered(message.id)

        val conversation = conversationRepository.findById(message.conversationId)
        val members = conversationRepository.findMembersByConversationId(message.conversationId)

        // A message can be scheduled before a block and come due after it. Withholding the
        // delivery rows and the fan-out is not enough on its own: the row was written at
        // schedule time and history filters on `isDeleted`, not on `isScheduled`, so it would
        // simply appear in the recipient's chat. Soft-deleting restores the invariant the
        // immediate path gets for free — no readable row, for either party.
        if (directSendBlock.isBlocked(conversation, members, message.senderId)) {
            messageRepository.softDelete(message.id)
            log.info(
                "Scheduled message dropped, recipient has blocked the sender: id={}, conv={}",
                message.id,
                message.conversationId
            )
            return null
        }

        val recipients = members.filter { it.userId != message.senderId }
        messageRepository.saveDeliveryStatuses(sentStatusesFor(message.id, recipients))
        return recipients
    }

    companion object {
        /**
         * How many due messages one scheduled run will take on.
         *
         * The query had no bound at all, so a backlog was loaded whole into one list and, before
         * #560, into one transaction. Per-message transactions remove the second problem; the cap
         * removes the first and keeps a run's duration predictable, which matters because the job
         * is `fixedDelay` — a long run delays the next one rather than overlapping it. At one run a
         * minute this drains 12,000 messages an hour, far above anything this product will
         * schedule, and the leftovers are simply picked up by the next run in the same order.
         */
        private const val SCHEDULED_BATCH_LIMIT = 200
    }
}
