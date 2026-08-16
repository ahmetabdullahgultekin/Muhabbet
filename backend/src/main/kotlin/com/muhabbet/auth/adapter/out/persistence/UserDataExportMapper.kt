package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.domain.model.ExportedConversationMembership
import com.muhabbet.auth.domain.model.ExportedMessage
import com.muhabbet.auth.domain.model.MessageDirection
import java.time.Instant
import java.util.UUID

/**
 * Pure row-to-domain mapping for the KVKK data export (#341). Kept separate from
 * [UserDataQueryPersistenceAdapter] and free of JPA/EntityManager so the privacy-sensitive rules
 * here — direction, content redaction on delete, and which counterparty fields survive — are unit
 * testable without a database.
 */
internal object UserDataExportMapper {

    /**
     * [senderId] `== requestingUserId` makes this a SENT message; anything else is RECEIVED — the
     * row is already scoped to "sent by me OR in a conversation I currently belong to", so there is
     * no third case. [senderDisplayName] is only carried through for RECEIVED messages: for a SENT
     * message it would just be the requesting user's own name, and surfacing it on every row of
     * their own history adds nothing.
     *
     * A deleted message keeps its `content`/`media_url` in the database (soft delete), but the app
     * itself never shows that content again once deleted — the export mirrors that instead of
     * resurrecting text the user, or the other side of the conversation, chose to delete.
     */
    fun toExportedMessage(
        id: UUID,
        conversationId: UUID,
        senderId: UUID,
        requestingUserId: UUID,
        contentType: String,
        content: String,
        mediaUrl: String?,
        replyToId: UUID?,
        forwardedFromId: UUID?,
        serverTimestamp: Instant,
        clientTimestamp: Instant,
        editedAt: Instant?,
        isDeleted: Boolean,
        senderDisplayName: String?
    ): ExportedMessage {
        val direction = if (senderId == requestingUserId) MessageDirection.SENT else MessageDirection.RECEIVED
        return ExportedMessage(
            id = id,
            conversationId = conversationId,
            direction = direction,
            counterpartyDisplayName = if (direction == MessageDirection.RECEIVED) senderDisplayName else null,
            contentType = contentType,
            content = if (isDeleted) null else content,
            mediaUrl = if (isDeleted) null else mediaUrl,
            replyToId = replyToId,
            forwardedFromId = forwardedFromId,
            serverTimestamp = serverTimestamp,
            clientTimestamp = clientTimestamp,
            editedAt = editedAt,
            isDeleted = isDeleted
        )
    }

    /**
     * [rawOtherParticipantDisplayName] is only meaningful for a direct conversation (exactly one
     * counterparty); a group has no single "other participant", so it is dropped for any type other
     * than `"direct"` regardless of what the query happened to resolve.
     */
    fun toExportedConversationMembership(
        conversationId: UUID,
        type: String,
        name: String?,
        role: String,
        joinedAt: Instant,
        mutedUntil: Instant?,
        pinned: Boolean,
        archived: Boolean,
        lastReadAt: Instant?,
        rawOtherParticipantDisplayName: String?
    ): ExportedConversationMembership = ExportedConversationMembership(
        conversationId = conversationId,
        type = type,
        name = name,
        otherParticipantDisplayName = if (type == "direct") rawOtherParticipantDisplayName else null,
        role = role,
        joinedAt = joinedAt,
        mutedUntil = mutedUntil,
        pinned = pinned,
        archived = archived,
        lastReadAt = lastReadAt
    )
}
