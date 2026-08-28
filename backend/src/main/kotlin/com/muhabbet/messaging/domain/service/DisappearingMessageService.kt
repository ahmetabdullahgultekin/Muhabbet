package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.`in`.ExpireDisappearingMessagesUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageDisappearingMessageUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class DisappearingMessageService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val messageBroadcaster: MessageBroadcaster,
    private val transactions: TransactionRunner
) : ManageDisappearingMessageUseCase, ExpireDisappearingMessagesUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * How many due messages one sweep will take on.
         *
         * Same reasoning as `MessageService.SCHEDULED_BATCH_LIMIT`, and the same shape of risk: the
         * sweep is `fixedDelay`, so one long run delays the next rather than overlapping it, and it
         * now broadcasts as well as deletes. At one run a minute this clears 12,000 messages an
         * hour; anything left over is picked up by the next run, oldest first.
         */
        internal const val EXPIRY_BATCH_LIMIT = 200
    }

    @Transactional
    override fun setDisappearTimer(conversationId: UUID, userId: UUID, seconds: Int?) {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.CONV_NOT_FOUND)

        val updated = conversation.copy(
            disappearAfterSeconds = seconds,
            updatedAt = Instant.now()
        )
        conversationRepository.updateConversation(updated)
        log.info("Disappear timer set: conv={}, seconds={}, by={}", conversationId, seconds, userId)
    }

    /**
     * Deletes what is due and then says so out loud.
     *
     * The saying-so is the whole point of #513. The sweep already deleted the rows correctly — the
     * owner measured it — but it told nobody, so a chat that was open at the moment of expiry went
     * on rendering the message until the user left and came back. A feature whose entire promise is
     * a bound on how long a message exists cannot leave that bound to whether someone happens to
     * navigate away.
     *
     * Members are fetched per conversation in one batch call rather than one per message: a timer
     * is a property of a conversation, so a sweep of 200 expired messages is very often two or
     * three conversations, and asking for the member list once per message is how this job would
     * become the most expensive query on the box.
     *
     * **The fan-out is outside the transaction, and the method carries no `@Transactional`.** Both
     * halves matter, for two different reasons. Ordering: a member who reacts to the frame by
     * re-fetching must not be able to read the row before the delete commits and be handed the
     * message straight back — broadcasting from inside the transaction is exactly what would allow
     * that. Resources: this publishes once per expired message, up to a batch of
     * [EXPIRY_BATCH_LIMIT], and `@Transactional` would hold one of the twenty pool connections for
     * the whole fan-out, which is #491 in a background job. `TransactionRunner` is `REQUIRED`
     * propagation, so leaving a method-level annotation on would have joined the two back together
     * and bought neither.
     *
     * A broadcast that fails leaves a message on screen until the next reload — the behaviour that
     * existed before this method — so it is deliberately not worth undoing a committed delete over.
     */
    override fun expireDueMessages(): Int {
        val now = Instant.now()
        val due = transactions.inTransaction {
            val expired = messageRepository.findExpiredMessages(now, EXPIRY_BATCH_LIMIT)
            if (expired.isNotEmpty()) {
                messageRepository.softDeleteExpired(expired.map { it.id }, now)
            }
            expired
        }
        if (due.isEmpty()) return 0

        val membersByConversation =
            conversationRepository.findMembersByConversationIds(due.map { it.conversationId }.distinct())

        due.forEach { message ->
            val recipients = membersByConversation[message.conversationId]?.map { it.userId }.orEmpty()
            if (recipients.isEmpty()) return@forEach
            messageBroadcaster.broadcastToUsers(
                recipients,
                WsMessage.MessageExpired(
                    messageId = message.id.toString(),
                    conversationId = message.conversationId.toString(),
                    expiredAt = now.toEpochMilli()
                )
            )
        }

        log.info("Expired {} disappearing message(s)", due.size)
        return due.size
    }
}
