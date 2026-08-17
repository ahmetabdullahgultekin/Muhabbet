package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.domain.model.MessageDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Covers the privacy-sensitive rules in #341's data export: which side of a conversation a message
 * belongs to, that a deleted message's content does not resurface, and that a group conversation
 * never gets a single "other participant" the way a direct conversation does. Pure functions, no
 * database needed.
 */
class UserDataExportMapperTest {

    private val requestingUserId = UUID.randomUUID()
    private val otherUserId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()
    private val messageId = UUID.randomUUID()
    private val now = Instant.parse("2026-08-16T10:00:00Z")

    @Nested
    inner class ToExportedMessage {

        @Test
        fun `should mark direction SENT when sender is the requesting user`() {
            val result = UserDataExportMapper.toExportedMessage(
                id = messageId,
                conversationId = conversationId,
                senderId = requestingUserId,
                requestingUserId = requestingUserId,
                contentType = "text",
                content = "hello",
                mediaUrl = null,
                replyToId = null,
                forwardedFromId = null,
                serverTimestamp = now,
                clientTimestamp = now,
                editedAt = null,
                isDeleted = false,
                senderDisplayName = "Me",
                viewOnce = false
            )

            assertEquals(MessageDirection.SENT, result.direction)
        }

        @Test
        fun `should mark direction RECEIVED when sender is not the requesting user`() {
            val result = UserDataExportMapper.toExportedMessage(
                id = messageId,
                conversationId = conversationId,
                senderId = otherUserId,
                requestingUserId = requestingUserId,
                contentType = "text",
                content = "hi there",
                mediaUrl = null,
                replyToId = null,
                forwardedFromId = null,
                serverTimestamp = now,
                clientTimestamp = now,
                editedAt = null,
                isDeleted = false,
                senderDisplayName = "Ayşe",
                viewOnce = false
            )

            assertEquals(MessageDirection.RECEIVED, result.direction)
        }

        @Test
        fun `should carry counterparty display name only for a received message`() {
            val received = UserDataExportMapper.toExportedMessage(
                id = messageId, conversationId = conversationId, senderId = otherUserId,
                requestingUserId = requestingUserId, contentType = "text", content = "hi",
                mediaUrl = null, replyToId = null, forwardedFromId = null,
                serverTimestamp = now, clientTimestamp = now, editedAt = null, isDeleted = false,
                senderDisplayName = "Ayşe",
                viewOnce = false
            )
            val sent = UserDataExportMapper.toExportedMessage(
                id = messageId, conversationId = conversationId, senderId = requestingUserId,
                requestingUserId = requestingUserId, contentType = "text", content = "hi",
                mediaUrl = null, replyToId = null, forwardedFromId = null,
                serverTimestamp = now, clientTimestamp = now, editedAt = null, isDeleted = false,
                senderDisplayName = "Me",
                viewOnce = false
            )

            assertEquals("Ayşe", received.counterpartyDisplayName)
            assertNull(sent.counterpartyDisplayName, "a SENT message must not carry a counterparty name")
        }

        @Test
        fun `should redact content and media url when the message was deleted`() {
            val result = UserDataExportMapper.toExportedMessage(
                id = messageId, conversationId = conversationId, senderId = requestingUserId,
                requestingUserId = requestingUserId, contentType = "image",
                content = "this should not resurface",
                mediaUrl = "https://cdn.example/secret.jpg",
                replyToId = null, forwardedFromId = null,
                serverTimestamp = now, clientTimestamp = now, editedAt = null, isDeleted = true,
                senderDisplayName = null,
                viewOnce = false
            )

            assertNull(result.content)
            assertNull(result.mediaUrl)
            assertEquals(true, result.isDeleted)
        }

        @Test
        fun `should preserve content and media url when the message was not deleted`() {
            val result = UserDataExportMapper.toExportedMessage(
                id = messageId, conversationId = conversationId, senderId = requestingUserId,
                requestingUserId = requestingUserId, contentType = "image", content = "hello",
                mediaUrl = "https://cdn.example/photo.jpg",
                replyToId = null, forwardedFromId = null,
                serverTimestamp = now, clientTimestamp = now, editedAt = null, isDeleted = false,
                senderDisplayName = null,
                viewOnce = false
            )

            assertEquals("hello", result.content)
            assertEquals("https://cdn.example/photo.jpg", result.mediaUrl)
        }

        @Test
        fun `should redact the media url of a view-once message`() {
            val result = UserDataExportMapper.toExportedMessage(
                id = messageId, conversationId = conversationId, senderId = requestingUserId,
                requestingUserId = requestingUserId, contentType = "image", content = "Photo",
                mediaUrl = "https://cdn.example/sealed.jpg?X-Amz-Signature=deadbeef",
                replyToId = null, forwardedFromId = null,
                serverTimestamp = now, clientTimestamp = now, editedAt = null, isDeleted = false,
                senderDisplayName = null,
                viewOnce = true
            )

            // A KVKK export is a legitimate way out of the product for anything the product would
            // still show you. It shows nobody this: the URL is presigned and needs no credential,
            // so exporting it would hand back in a zip exactly what the seal exists to withhold.
            assertNull(result.mediaUrl)
            assertEquals("Photo", result.content)
        }
    }

    @Nested
    inner class ToExportedConversationMembership {

        @Test
        fun `should carry other participant display name through for a direct conversation`() {
            val result = UserDataExportMapper.toExportedConversationMembership(
                conversationId = conversationId,
                type = "direct",
                name = null,
                role = "member",
                joinedAt = now,
                mutedUntil = null,
                pinned = false,
                archived = false,
                lastReadAt = null,
                rawOtherParticipantDisplayName = "Ayşe"
            )

            assertEquals("Ayşe", result.otherParticipantDisplayName)
        }

        @Test
        fun `should drop other participant display name for a group conversation`() {
            val result = UserDataExportMapper.toExportedConversationMembership(
                conversationId = conversationId,
                type = "group",
                name = "Weekend Trip",
                role = "member",
                joinedAt = now,
                mutedUntil = null,
                pinned = false,
                archived = false,
                lastReadAt = null,
                // Even if the query resolved a name (some arbitrary other member), a group has no
                // single "other participant" — the mapper must not surface it.
                rawOtherParticipantDisplayName = "Some Other Member"
            )

            assertNull(result.otherParticipantDisplayName)
            assertEquals("Weekend Trip", result.name)
        }
    }
}
