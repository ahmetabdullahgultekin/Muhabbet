package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.PinnedMessage
import com.muhabbet.messaging.domain.port.`in`.ManagePinnedMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.toInfo
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PinnedMessageRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Pin and unpin both tell every member the conversation's new set of pins (#790), as a snapshot,
 * after the write has committed — the same boundary every other fan-out in this module keeps, which
 * is why neither method carries `@Transactional` (see `GroupService`).
 */
open class PinnedMessageService(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val pinnedMessageRepository: PinnedMessageRepository,
    private val messageBroadcaster: MessageBroadcaster,
    private val transactions: TransactionRunner
) : ManagePinnedMessageUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_PINS_PER_CONVERSATION = 3
    }

    /** What every member is told after a change: the new snapshot, and who to tell. */
    private class PinSnapshot(val pins: List<PinnedMessage>, val recipients: List<UUID>)

    /** [snapshot] is null when nothing changed — a re-pin of a pinned message tells nobody. */
    private class PinOutcome(val pin: PinnedMessage, val snapshot: PinSnapshot?)

    override fun pin(conversationId: UUID, messageId: UUID, userId: UUID): PinnedMessage {
        val outcome = transactions.inTransaction { persistPin(conversationId, messageId, userId) }
        outcome.snapshot?.let { snapshot ->
            broadcast(conversationId, userId, snapshot)
            log.info("Message pinned: conv={}, msg={}, by={}", conversationId, messageId, userId)
        }
        return outcome.pin
    }

    override fun unpin(conversationId: UUID, messageId: UUID, userId: UUID) {
        // Unpinning something that was not pinned changes nothing, so it tells nobody anything.
        // The list stands in for an optional, because inTransaction takes no null result.
        val snapshots = transactions.inTransaction {
            requireMember(conversationId, userId)
            val existed = pinnedMessageRepository.find(conversationId, messageId) != null
            pinnedMessageRepository.delete(conversationId, messageId)
            if (existed) listOf(snapshotOf(conversationId)) else emptyList()
        }
        snapshots.firstOrNull()?.let { snapshot ->
            broadcast(conversationId, userId, snapshot)
            log.info("Message unpinned: conv={}, msg={}, by={}", conversationId, messageId, userId)
        }
    }

    @Transactional(readOnly = true)
    override fun getPinned(conversationId: UUID, userId: UUID): List<PinnedMessage> {
        requireMember(conversationId, userId)
        return pinnedMessageRepository.findByConversationId(conversationId)
    }

    private fun persistPin(conversationId: UUID, messageId: UUID, userId: UUID): PinOutcome {
        requireMember(conversationId, userId)

        // The message must belong to this conversation; one in another conversation answers exactly
        // like a missing one, so its existence does not leak across conversations.
        messageRepository.findById(messageId)
            ?.takeIf { it.conversationId == conversationId }
            ?: throw BusinessException(ErrorCode.MSG_NOT_FOUND)

        // Idempotent: re-pinning an already-pinned message is a no-op that returns the existing pin.
        pinnedMessageRepository.find(conversationId, messageId)?.let { existing ->
            return PinOutcome(pin = existing, snapshot = null)
        }

        if (pinnedMessageRepository.countByConversationId(conversationId) >= MAX_PINS_PER_CONVERSATION) {
            throw BusinessException(ErrorCode.MSG_PIN_LIMIT_REACHED)
        }

        val saved = pinnedMessageRepository.save(
            PinnedMessage(conversationId = conversationId, messageId = messageId, pinnedBy = userId)
        )
        return PinOutcome(pin = saved, snapshot = snapshotOf(conversationId))
    }

    private fun snapshotOf(conversationId: UUID): PinSnapshot = PinSnapshot(
        pins = pinnedMessageRepository.findByConversationId(conversationId),
        recipients = conversationRepository.findMembersByConversationId(conversationId).map { it.userId }
    )

    private fun broadcast(conversationId: UUID, changedBy: UUID, snapshot: PinSnapshot) {
        messageBroadcaster.broadcastToUsers(
            snapshot.recipients,
            WsMessage.PinnedMessagesChanged(
                conversationId = conversationId.toString(),
                changedBy = changedBy.toString(),
                pins = snapshot.pins.map { it.toInfo() }
            )
        )
    }

    private fun requireMember(conversationId: UUID, userId: UUID) {
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
    }
}
