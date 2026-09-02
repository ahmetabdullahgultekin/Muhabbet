package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Conversation
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.ResolvedAttachment
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.InlineTransactionRunner
import com.muhabbet.shared.TestMediaAttachmentPolicy
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * #679 — a sender must not be able to choose what address the recipient's phone connects to.
 *
 * The harm is not that a bad URL is stored; it is that the recipient's client fetches it. Coil is
 * handed `message.mediaUrl` the moment the bubble composes — `MessageBubble.kt` does it for images,
 * GIFs, stickers and video posters — with no tap and no prompt. So a sender who names their own
 * server learns the recipient's IP address and the exact moment they opened the chat: a read
 * receipt collected around the outside of the read-receipt setting the server started enforcing in
 * #377.
 *
 * That makes "did the send throw" the wrong assertion on its own. **What has to be true is that no
 * foreign address is ever handed to a recipient**, so every test here asserts on what was saved and
 * what was broadcast, not merely on the exception. A test that only proved a happy path would
 * reproduce the bug: the old code stored and delivered the attacker's string perfectly happily.
 *
 * The policy port is faked rather than mocked, so these tests exercise the service's *use* of the
 * rule. Whether the rule itself can be fooled by a URL two parsers read differently is
 * `MediaAttachmentPolicyAdapterTest`, against the real implementation.
 */
class MediaAttachmentPolicyTest {

    private val sender = UUID.randomUUID()
    private val recipient = UUID.randomUUID()
    private val conversationId = UUID.randomUUID()

    private val ourUrl = "https://cdn.example/muhabbet-media/images/photo.jpg?X-Amz-Signature=abc"
    private val attackerUrl = "https://attacker.test/beacon.gif"

    private val conversationRepository: ConversationRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val messageBroadcaster: MessageBroadcaster = mockk(relaxed = true)
    private val userDirectory: UserDirectoryPort = mockk(relaxed = true)
    private val readReceiptPolicy: ReadReceiptPolicyPort = mockk(relaxed = true)
    private val blockPolicy: BlockPolicyPort = mockk(relaxed = true)

    private fun service(policy: TestMediaAttachmentPolicy = TestMediaAttachmentPolicy()): MessageService {
        every { conversationRepository.findMember(conversationId, sender) } returns
            ConversationMember(conversationId = conversationId, userId = sender, role = MemberRole.MEMBER)
        every { conversationRepository.findById(conversationId) } returns
            Conversation(id = conversationId, type = ConversationType.DIRECT)
        every { conversationRepository.findMembersByConversationId(conversationId) } returns listOf(
            ConversationMember(conversationId = conversationId, userId = sender, role = MemberRole.MEMBER),
            ConversationMember(conversationId = conversationId, userId = recipient, role = MemberRole.MEMBER)
        )
        every { blockPolicy.hasBlocked(any(), any()) } returns false
        every { blockPolicy.findBlockedBy(any(), any()) } returns emptySet()
        every { messageRepository.existsById(any()) } returns false
        every { messageRepository.save(any()) } answers { firstArg() }

        return MessageService(
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            messageBroadcaster = messageBroadcaster,
            userDirectory = userDirectory,
            readReceiptPolicy = readReceiptPolicy,
            blockPolicy = blockPolicy,
            mediaAttachmentPolicy = policy,
            transactions = InlineTransactionRunner()
        )
    }

    private fun send(
        mediaUrl: String? = null,
        thumbnailUrl: String? = null,
        mediaId: UUID? = null
    ) = SendMessageCommand(
        messageId = UUID.randomUUID(),
        conversationId = conversationId,
        senderId = sender,
        content = "",
        contentType = ContentType.IMAGE,
        mediaUrl = mediaUrl,
        thumbnailUrl = thumbnailUrl,
        mediaId = mediaId,
        clientTimestamp = Instant.now()
    )

    @Test
    fun `should refuse a message whose media address is not ours`() {
        val service = service()

        val thrown = assertThrows<BusinessException> { service.sendMessage(send(mediaUrl = attackerUrl)) }

        assertAll(
            { assertEquals(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE, thrown.errorCode) },
            // The two claims that matter: the address is not stored, so history and background sync
            // cannot serve it later, and it is not fanned out, so no live client fetches it now.
            { verify(exactly = 0) { messageRepository.save(any()) } },
            { verify(exactly = 0) { messageBroadcaster.broadcastMessage(any(), any()) } }
        )
    }

    @Test
    fun `should refuse a message whose thumbnail address is not ours`() {
        val service = service()

        // The bubble draws the thumbnail for video, so this URL is fetched exactly as eagerly as the
        // other one. Checking only `mediaUrl` would leave the whole attack working through the field
        // next to it.
        val thrown = assertThrows<BusinessException> {
            service.sendMessage(send(mediaUrl = ourUrl, thumbnailUrl = attackerUrl))
        }

        assertAll(
            { assertEquals(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE, thrown.errorCode) },
            { verify(exactly = 0) { messageRepository.save(any()) } },
            { verify(exactly = 0) { messageBroadcaster.broadcastMessage(any(), any()) } }
        )
    }

    @Test
    fun `should discard the client's address entirely when the media id resolves to the sender's own upload`() {
        val mediaId = UUID.randomUUID()
        val serverUrl = "https://cdn.example/muhabbet-media/images/server-minted.jpg?X-Amz-Signature=zzz"
        val service = service(
            TestMediaAttachmentPolicy(
                owned = mapOf(
                    mediaId to TestMediaAttachmentPolicy.OwnedMedia(
                        uploaderId = sender,
                        attachment = ResolvedAttachment(serverUrl, "$serverUrl&thumb")
                    )
                )
            )
        )
        val saved = slot<Message>()
        every { messageRepository.save(capture(saved)) } answers { saved.captured }

        // The client asserts an id it really owns *and* a foreign URL. The strong path does not
        // compare the two or reject the send — it never reads the client's string at all.
        service.sendMessage(send(mediaUrl = attackerUrl, thumbnailUrl = attackerUrl, mediaId = mediaId))

        assertAll(
            { assertEquals(serverUrl, saved.captured.mediaUrl) },
            { assertEquals("$serverUrl&thumb", saved.captured.thumbnailUrl) },
            { assertEquals(mediaId, saved.captured.mediaId) }
        )
    }

    @Test
    fun `should still refuse a foreign address when the media id names someone else's blob`() {
        val mediaId = UUID.randomUUID()
        val service = service(
            TestMediaAttachmentPolicy(
                owned = mapOf(
                    mediaId to TestMediaAttachmentPolicy.OwnedMedia(
                        uploaderId = recipient,
                        attachment = ResolvedAttachment("https://cdn.example/not-yours.jpg", null)
                    )
                )
            )
        )

        // An id the sender does not own resolves to nothing, so the send falls back to the host
        // test — it must not fall back to trusting the client, and it must not hand out a blob
        // belonging to somebody else either.
        val thrown = assertThrows<BusinessException> {
            service.sendMessage(send(mediaUrl = attackerUrl, mediaId = mediaId))
        }

        assertAll(
            { assertEquals(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE, thrown.errorCode) },
            { verify(exactly = 0) { messageRepository.save(any()) } }
        )
    }

    @Test
    fun `should accept a forward, which carries our own address and no media id`() {
        val service = service()
        val saved = slot<Message>()
        every { messageRepository.save(capture(saved)) } answers { saved.captured }

        // Forwarding deliberately does not carry `mediaId` — the blob belongs to whoever first sent
        // it, and claiming it would give the forwarder the power to destroy it (#541). So the only
        // thing checkable is the address, and this is the case that would break if the fix had
        // demanded an id.
        service.sendMessage(send(mediaUrl = ourUrl, mediaId = null))

        assertAll(
            { assertEquals(ourUrl, saved.captured.mediaUrl) },
            { assertNull(saved.captured.mediaId) },
            { verify(exactly = 1) { messageBroadcaster.broadcastMessage(any(), any()) } }
        )
    }

    @Test
    fun `should leave a plain text message untouched`() {
        // A policy that refuses everything: a text send must never reach it, which is what keeps the
        // check off the hot path for the overwhelming majority of messages.
        val service = service(TestMediaAttachmentPolicy(ownHost = "https://nothing-matches"))
        val saved = slot<Message>()
        every { messageRepository.save(capture(saved)) } answers { saved.captured }

        service.sendMessage(
            SendMessageCommand(
                messageId = UUID.randomUUID(),
                conversationId = conversationId,
                senderId = sender,
                content = "hello",
                contentType = ContentType.TEXT,
                clientTimestamp = Instant.now()
            )
        )

        assertAll(
            { assertEquals("hello", saved.captured.content) },
            { assertNull(saved.captured.mediaUrl) }
        )
    }
}
