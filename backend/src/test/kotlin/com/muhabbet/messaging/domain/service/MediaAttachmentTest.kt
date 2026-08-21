package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.SendMessageCommand
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MediaAttachment
import com.muhabbet.messaging.domain.port.out.MediaAttachmentPolicyPort
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.ReadReceiptPolicyPort
import com.muhabbet.messaging.domain.port.out.StatusRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.InlineTransactionRunner
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * A message's `mediaUrl` used to be written to the database and handed to the recipient's image
 * loader exactly as the sender typed it (#679). The recipient's phone fetches it on render, with
 * no tap, so the sender could choose any address in the world and be told the moment their message
 * appeared on someone else's screen — an IP address and a read receipt, from an app whose privacy
 * settings the user believes govern both.
 *
 * These tests are about *provenance*, so none of them asserts merely that a send succeeded. Each
 * one names where the URL came from and what the server is entitled to conclude from that.
 */
class MediaAttachmentTest {

    private lateinit var conversationRepository: ConversationRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var messageBroadcaster: MessageBroadcaster
    private lateinit var userDirectory: UserDirectoryPort
    private lateinit var readReceiptPolicy: ReadReceiptPolicyPort
    private lateinit var blockPolicy: BlockPolicyPort
    private lateinit var statusRepository: StatusRepository
    private lateinit var mediaAttachmentPolicy: MediaAttachmentPolicyPort
    private lateinit var messageService: MessageService
    private lateinit var statusService: StatusService

    private val sender = TestData.USER_ID_1
    private val stranger = TestData.USER_ID_2
    private val conversationId = TestData.CONVERSATION_ID

    /** What the media host hands back after an upload: our origin, the bucket, then the object key. */
    private val ownUpload = "https://cdn-muhabbet.example.test/muhabbet-media/images/$sender/photo.jpg?X-Amz-Signature=abc"
    private val ownThumbnail = "https://cdn-muhabbet.example.test/muhabbet-media/thumbnails/$sender/photo.jpg?X-Amz-Signature=abc"
    private val strangersUpload = "https://cdn-muhabbet.example.test/muhabbet-media/images/$stranger/private.jpg?X-Amz-Signature=abc"
    private val attackerBeacon = "https://tracker.attacker.example/beacon.png"

    @BeforeEach
    fun setUp() {
        conversationRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        messageBroadcaster = mockk(relaxed = true)
        userDirectory = mockk(relaxed = true)
        readReceiptPolicy = mockk(relaxed = true)
        blockPolicy = mockk(relaxed = true)
        statusRepository = mockk(relaxed = true)
        mediaAttachmentPolicy = mockk()

        // Stands in for the adapter over the media module. The three answers are the three things a
        // URL can be: a blob of ours with a known uploader, the sticker host, or an address we have
        // never heard of. MediaAttachmentPolicyAdapterTest covers the mapping from URL to answer.
        every { mediaAttachmentPolicy.classify(ownUpload) } returns MediaAttachment.OwnStorage(sender)
        every { mediaAttachmentPolicy.classify(ownThumbnail) } returns MediaAttachment.OwnStorage(sender)
        every { mediaAttachmentPolicy.classify(strangersUpload) } returns MediaAttachment.OwnStorage(stranger)
        every { mediaAttachmentPolicy.classify(match { it.contains("giphy.com") }) } returns
            MediaAttachment.PublicStickerHost
        every { mediaAttachmentPolicy.classify(attackerBeacon) } returns MediaAttachment.Unrecognised

        every { blockPolicy.hasBlocked(any(), any()) } returns false
        every { conversationRepository.findMember(conversationId, sender) } returns
            ConversationMember(conversationId = conversationId, userId = sender)
        every { messageRepository.existsById(any()) } returns false
        every { messageRepository.save(any()) } answers { firstArg() }
        every { statusRepository.save(any()) } answers { firstArg() }

        messageService = MessageService(
            conversationRepository = conversationRepository,
            messageRepository = messageRepository,
            messageBroadcaster = messageBroadcaster,
            userDirectory = userDirectory,
            readReceiptPolicy = readReceiptPolicy,
            blockPolicy = blockPolicy,
            transactions = InlineTransactionRunner(),
            mediaAttachmentPolicy = mediaAttachmentPolicy
        )
        statusService = StatusService(
            statusRepository,
            conversationRepository,
            userDirectory,
            blockPolicy,
            mediaAttachmentPolicy
        )
    }

    private fun send(
        mediaUrl: String? = null,
        thumbnailUrl: String? = null,
        forwardedFrom: UUID? = null,
        contentType: ContentType = ContentType.IMAGE,
        content: String = ""
    ) = messageService.sendMessage(
        SendMessageCommand(
            messageId = UUID.randomUUID(),
            conversationId = conversationId,
            senderId = sender,
            content = content,
            contentType = contentType,
            mediaUrl = mediaUrl,
            thumbnailUrl = thumbnailUrl,
            forwardedFrom = forwardedFrom,
            clientTimestamp = Instant.now()
        )
    )

    // ─── the hole ────────────────────────────────────────

    @Test
    fun `should reject a mediaUrl pointing at an address the sender chose`() {
        val thrown = assertThrows<BusinessException> { send(mediaUrl = attackerBeacon) }
        assertEquals(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE, thrown.errorCode)
    }

    /**
     * The thumbnail is the more dangerous of the two: an image bubble renders its thumbnail
     * immediately and its full-size media only when tapped, so a beacon parked here fires on
     * render for certain.
     */
    @Test
    fun `should reject a thumbnailUrl pointing at an address the sender chose`() {
        val thrown = assertThrows<BusinessException> {
            send(mediaUrl = ownUpload, thumbnailUrl = attackerBeacon)
        }
        assertEquals(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE, thrown.errorCode)
    }

    /**
     * Our own host is not a password. Being able to name a blob is not the same as being allowed to
     * publish it, which is the whole reason the check is ownership against `media_files` and not a
     * test on the URL's shape.
     */
    @Test
    fun `should reject media uploaded by someone else when not forwarding`() {
        val thrown = assertThrows<BusinessException> { send(mediaUrl = strangersUpload) }
        assertEquals(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE, thrown.errorCode)
    }

    @Test
    fun `should reject a status mediaUrl pointing at an address the poster chose`() {
        val thrown = assertThrows<BusinessException> {
            statusService.createStatus(sender, content = null, mediaUrl = attackerBeacon)
        }
        assertEquals(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE, thrown.errorCode)
    }

    // ─── what must keep working ──────────────────────────

    @Test
    fun `should accept media the sender uploaded`() {
        val message = send(mediaUrl = ownUpload, thumbnailUrl = ownThumbnail)
        assertEquals(ownUpload, message.mediaUrl)
        assertEquals(ownThumbnail, message.thumbnailUrl)
    }

    @Test
    fun `should accept a status whose media the poster uploaded`() {
        val status = statusService.createStatus(sender, content = null, mediaUrl = ownUpload)
        assertEquals(ownUpload, status.mediaUrl)
    }

    /**
     * Forwarding a photo someone sent you is the ordinary case where the sender is not the
     * uploader. What makes it safe is not the `forwardedFrom` flag — the client sets that — but the
     * message it names: the server looks the original up and requires the forwarder to be a member
     * of the conversation it lives in, and requires the URL to be the one that message actually
     * carries.
     */
    @Test
    fun `should accept a forward of media a conversation the sender belongs to already carries`() {
        val originalId = UUID.randomUUID()
        val sourceConversation = UUID.randomUUID()
        every { messageRepository.findById(originalId) } returns Message(
            id = originalId,
            conversationId = sourceConversation,
            senderId = stranger,
            contentType = ContentType.IMAGE,
            content = "",
            mediaUrl = strangersUpload,
            serverTimestamp = Instant.now(),
            clientTimestamp = Instant.now()
        )
        every { conversationRepository.findMember(sourceConversation, sender) } returns
            ConversationMember(conversationId = sourceConversation, userId = sender)

        val message = send(mediaUrl = strangersUpload, forwardedFrom = originalId)

        assertEquals(strangersUpload, message.mediaUrl)
    }

    @Test
    fun `should reject a forward naming a message in a conversation the sender cannot see`() {
        val originalId = UUID.randomUUID()
        val sourceConversation = UUID.randomUUID()
        every { messageRepository.findById(originalId) } returns Message(
            id = originalId,
            conversationId = sourceConversation,
            senderId = stranger,
            contentType = ContentType.IMAGE,
            content = "",
            mediaUrl = strangersUpload,
            serverTimestamp = Instant.now(),
            clientTimestamp = Instant.now()
        )
        every { conversationRepository.findMember(sourceConversation, sender) } returns null

        val thrown = assertThrows<BusinessException> {
            send(mediaUrl = strangersUpload, forwardedFrom = originalId)
        }
        assertEquals(ErrorCode.MSG_MEDIA_NOT_ACCESSIBLE, thrown.errorCode)
    }

    /**
     * GIFs and stickers come from Giphy, not from us — `GifStickerPicker` hands the chat screen a
     * `media*.giphy.com` URL and nothing is ever uploaded. A rule that only accepted our own blobs
     * would compile, pass every ownership test above, and silently delete a shipped feature.
     */
    @Test
    fun `should accept a sticker from the public GIF host the picker sends`() {
        val giphy = "https://media3.giphy.com/media/abc123/giphy.gif?cid=1&rid=giphy.gif"
        val message = send(mediaUrl = giphy, contentType = ContentType.STICKER)
        assertEquals(giphy, message.mediaUrl)
    }

    @Test
    fun `should accept a text message that carries no media at all`() {
        val message = send(contentType = ContentType.TEXT, content = "merhaba")
        assertEquals(null, message.mediaUrl)
    }
}
