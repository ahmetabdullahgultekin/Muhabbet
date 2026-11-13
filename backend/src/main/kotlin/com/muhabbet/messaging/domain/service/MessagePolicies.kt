package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.MessageDeliveryStatus
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.validation.ValidationRules
import java.util.UUID

/*
 * The rules the message services share, as small policies rather than a common base class (#894).
 *
 * They used to be private members of one 761-line `MessageService`, which is the only reason the
 * send, edit, receipt and scheduled-delivery paths could reuse them. Splitting the service by use
 * case meant giving each rule a home of its own; composition rather than inheritance so that each
 * service states which rules it applies and a change to one cannot reach a service that never
 * asked for it.
 */

/**
 * Whether a send (or an edit, or a scheduled delivery) is a direct message to someone who has
 * blocked the sender.
 *
 * **Direct conversations only, and deliberately so.** In a group a block does not stop the
 * message — WhatsApp behaves the same way — because a group message has one sender and many
 * readers, and dropping it because one of thirty members has blocked the sender would silently
 * remove it for the other twenty-nine. What a block owes you in a group is the ability to leave
 * it, not the power to censor a room.
 *
 * **One direction only.** The question is whether the *recipient* blocked the *sender*. Someone
 * messaging a person they themselves blocked is not the case with a victim, and swallowing their
 * own outgoing message would read as a bug rather than a policy.
 */
class DirectSendBlockPolicy(private val blockPolicy: BlockPolicyPort) {

    fun isBlocked(conversation: Conversation?, members: List<ConversationMember>, senderId: UUID): Boolean {
        if (conversation?.type != ConversationType.DIRECT) return false
        val recipientId = members.map { it.userId }.firstOrNull { it != senderId } ?: return false
        return blockPolicy.hasBlocked(recipientId, senderId)
    }
}

/**
 * The one place text content is judged, for both the send path and the edit path — which is the
 * point, because an edit that accepted what a send rejects would be a way around the limit.
 */
internal fun requireValidMessageContent(content: String) {
    if (ValidationRules.isValidMessageContent(content)) return
    throw BusinessException(
        if (content.isBlank()) ErrorCode.MSG_EMPTY_CONTENT else ErrorCode.MSG_CONTENT_TOO_LONG
    )
}

/**
 * READ from a reader who has receipts off is published as DELIVERED — the sender learns the
 * message arrived, never that it was opened. Every other status passes through untouched.
 *
 * Every place a status leaves the server goes through this: the live WebSocket update, the
 * history aggregate and the per-recipient "Info" list. Missing one of the three is how #620
 * happened.
 */
internal fun publishableStatus(
    readerId: UUID,
    status: DeliveryStatus,
    receiptsDisabled: Set<UUID>
): DeliveryStatus =
    if (status == DeliveryStatus.READ && readerId in receiptsDisabled) DeliveryStatus.DELIVERED else status

/** The SENT rows a newly delivered message starts with, one per recipient, for a batched insert. */
internal fun sentStatusesFor(messageId: UUID, recipients: List<ConversationMember>): List<MessageDeliveryStatus> =
    recipients.map { member ->
        MessageDeliveryStatus(messageId = messageId, userId = member.userId, status = DeliveryStatus.SENT)
    }
