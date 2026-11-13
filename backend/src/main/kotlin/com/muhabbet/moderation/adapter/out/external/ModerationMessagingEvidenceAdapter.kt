package com.muhabbet.moderation.adapter.out.external

import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.moderation.domain.model.MessageEvidence
import com.muhabbet.moderation.domain.port.out.ReportEvidencePort
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Bridges moderation's [ReportEvidencePort] to messaging's out-ports. Being an adapter, this is the
 * one class in moderation allowed to know messages live in messaging — the pattern
 * [ModerationAuthUserDirectoryAdapter] set for users.
 *
 * View-once media is snapshotted without its URL: the report keeps that the message existed and
 * what kind it was, but does not become a way of keeping a photo its recipient was only allowed to
 * see once (#515).
 */
@Component
class ModerationMessagingEvidenceAdapter(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository
) : ReportEvidencePort {

    override fun snapshotVisibleMessage(reporterId: UUID, messageId: UUID): MessageEvidence? {
        val message = messageRepository.findById(messageId)?.takeIf { !it.isDeleted && !it.isScheduled }
            ?: return null
        if (!isConversationMember(reporterId, message.conversationId)) return null
        return MessageEvidence(
            messageId = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            contentType = message.contentType.name,
            content = message.content,
            mediaUrl = if (message.viewOnce) null else message.mediaUrl,
            sentAt = message.serverTimestamp
        )
    }

    override fun isConversationMember(reporterId: UUID, conversationId: UUID): Boolean =
        conversationRepository.findMember(conversationId, reporterId) != null
}
