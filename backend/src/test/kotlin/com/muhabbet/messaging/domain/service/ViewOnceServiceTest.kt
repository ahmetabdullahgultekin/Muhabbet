package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MediaBytes
import com.muhabbet.messaging.domain.port.out.MediaObjectPort
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.UUID

/**
 * Opening a view-once message: who may, what they get, and what is left behind.
 *
 * The last question is the one #541 was about. Burning used to hide the photo from every screen and
 * leave the bytes in MinIO behind a **presigned** URL — no credential needed, because the URL *is*
 * the credential — minted with a seven-day expiry. So a "burned" photo stayed fetchable for the
 * rest of that week by anyone who had kept the string, and "view once" was a property of the UI
 * rather than of the data.
 *
 * The authorization tests came with this class from `MessagingServiceTest` when the burn was split
 * out; they are the IDOR guard from #55 and they still belong next to the destruction.
 */
class ViewOnceServiceTest {

    private lateinit var messageRepository: MessageRepository
    private lateinit var conversationRepository: ConversationRepository
    private lateinit var mediaObjects: MediaObjectPort
    private lateinit var service: ViewOnceService

    private val sender: UUID = UUID.randomUUID()
    private val recipient: UUID = UUID.randomUUID()
    private val stranger: UUID = UUID.randomUUID()
    private val convId: UUID = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        messageRepository = mockk(relaxed = true)
        conversationRepository = mockk(relaxed = true)
        mediaObjects = mockk(relaxed = true)
        service = ViewOnceService(messageRepository, conversationRepository, mediaObjects)
    }

    private fun message(mediaId: UUID? = null) = Message(
        id = UUID.randomUUID(),
        conversationId = convId,
        senderId = sender,
        content = "peek",
        contentType = ContentType.IMAGE,
        mediaUrl = "https://cdn.example/blob?sig=abc",
        thumbnailUrl = "https://cdn.example/thumb?sig=abc",
        mediaId = mediaId,
        viewOnce = true,
        clientTimestamp = Instant.now()
    )

    private fun stubOpenableBy(message: Message, userId: UUID) {
        every { messageRepository.findById(message.id) } returns message
        every { conversationRepository.findMember(convId, userId) } returns
            ConversationMember(conversationId = convId, userId = userId)
        every { messageRepository.markViewOnceViewed(message.id, userId, any()) } returns 1
    }

    // ─── Authorization (#55) ─────────────────────────────

    @Test
    fun `should mark when requester is a member and not the sender`() {
        val message = message()
        stubOpenableBy(message, recipient)

        service.markViewOnceViewed(message.id, recipient)

        verify(exactly = 1) { messageRepository.markViewOnceViewed(message.id, recipient, any()) }
    }

    @Test
    fun `should refuse the sender so a self-view cannot spend the recipient's look`() {
        val message = message()
        every { messageRepository.findById(message.id) } returns message
        every { conversationRepository.findMember(convId, sender) } returns
            ConversationMember(conversationId = convId, userId = sender)

        assertThrows<BusinessException> { service.markViewOnceViewed(message.id, sender) }

        verify(exactly = 0) { messageRepository.markViewOnceViewed(any(), any(), any()) }
    }

    @Test
    fun `should throw MSG_NOT_MEMBER and not burn the message for a non-member`() {
        val message = message()
        every { messageRepository.findById(message.id) } returns message
        // The stranger knows the messageId but is not in the conversation.
        every { conversationRepository.findMember(convId, stranger) } returns null

        val ex = assertThrows<BusinessException> { service.markViewOnceViewed(message.id, stranger) }

        assertEquals(ErrorCode.MSG_NOT_MEMBER, ex.errorCode)
        verify(exactly = 0) { messageRepository.markViewOnceViewed(any(), any(), any()) }
        // And nothing of anyone's is deleted on the way to being refused.
        verify(exactly = 0) { mediaObjects.takeAndDestroy(any()) }
    }

    @Test
    fun `should refuse a second opener when the burn updates no row`() {
        val message = message()
        every { messageRepository.findById(message.id) } returns message
        every { conversationRepository.findMember(convId, recipient) } returns
            ConversationMember(conversationId = convId, userId = recipient)
        // viewedAt is still null on the row this transaction read, so the cheap check passes. The
        // conditional UPDATE is what actually decides, and it matched nothing: someone else won.
        every { messageRepository.markViewOnceViewed(message.id, recipient, any()) } returns 0

        val ex = assertThrows<BusinessException> { service.markViewOnceViewed(message.id, recipient) }

        assertEquals(ErrorCode.MSG_VIEW_ONCE_ALREADY_VIEWED, ex.errorCode)
    }

    // ─── The bytes are actually destroyed (#541) ─────────

    @Test
    fun `should destroy the blob and hand back bytes instead of a url`() {
        val mediaId = UUID.randomUUID()
        val message = message(mediaId)
        stubOpenableBy(message, recipient)
        every { mediaObjects.findUploaderId(mediaId) } returns sender
        every { mediaObjects.takeAndDestroy(mediaId) } returns MediaBytes(byteArrayOf(7, 8, 9), "image/jpeg")

        val reveal = service.markViewOnceViewed(message.id, recipient)

        verify(exactly = 1) { mediaObjects.takeAndDestroy(mediaId) }
        assertArrayEquals(byteArrayOf(7, 8, 9), reveal.mediaBytes)
        assertEquals("image/jpeg", reveal.mediaContentType)
        // The security property, not a formatting preference: a URL is a credential that outlives
        // the response it travelled in, and "once" cannot be expressed as a duration.
        assertNull(reveal.mediaUrl)
        assertNull(reveal.thumbnailUrl)
    }

    @Test
    fun `should destroy the blob before marking the message spent`() {
        val mediaId = UUID.randomUUID()
        val message = message(mediaId)
        stubOpenableBy(message, recipient)
        every { mediaObjects.findUploaderId(mediaId) } returns sender
        every { mediaObjects.takeAndDestroy(mediaId) } returns MediaBytes(byteArrayOf(7), "image/jpeg")

        service.markViewOnceViewed(message.id, recipient)

        // Two writes to two systems that can fail independently. This order makes a failure fall as
        // "unreachable but still marked available" — the recipient loses their look. The other
        // order leaves it marked burned and still fetchable, which is the defect itself.
        verifyOrder {
            mediaObjects.takeAndDestroy(mediaId)
            messageRepository.markViewOnceViewed(message.id, recipient, any())
        }
    }

    @Test
    fun `should leave the blob destroyed even when the burn write loses the race`() {
        val mediaId = UUID.randomUUID()
        val message = message(mediaId)
        every { messageRepository.findById(message.id) } returns message
        every { conversationRepository.findMember(convId, recipient) } returns
            ConversationMember(conversationId = convId, userId = recipient)
        every { mediaObjects.findUploaderId(mediaId) } returns sender
        every { mediaObjects.takeAndDestroy(mediaId) } returns MediaBytes(byteArrayOf(7), "image/jpeg")
        every { messageRepository.markViewOnceViewed(message.id, recipient, any()) } returns 0

        assertThrows<BusinessException> { service.markViewOnceViewed(message.id, recipient) }

        // The delete already happened and is not undone — it is idempotent, and the winner did it
        // too. Nothing is released to the loser, which is the outcome that matters.
        verify(exactly = 1) { mediaObjects.takeAndDestroy(mediaId) }
    }

    @Test
    fun `should not destroy a blob the message's sender did not upload`() {
        // `media_id` arrives on the send frame and is a client assertion; acting on it deletes a
        // file. A message naming somebody else's photo is either a stale client or an attempt to
        // aim this at a file its sender never had, and either way nothing is deleted.
        val mediaId = UUID.randomUUID()
        val message = message(mediaId)
        stubOpenableBy(message, recipient)
        every { mediaObjects.findUploaderId(mediaId) } returns stranger

        val reveal = service.markViewOnceViewed(message.id, recipient)

        verify(exactly = 0) { mediaObjects.takeAndDestroy(any()) }
        assertNull(reveal.mediaBytes)
        // It cannot be destroyed, so the weaker promise applies and is not disguised.
        assertEquals("https://cdn.example/blob?sig=abc", reveal.mediaUrl)
    }

    @Test
    fun `should fall back to the stored url when the message has no media reference`() {
        // Rows written before V24 carry no reference, so there is no object to find and none to
        // destroy. Showing the recipient nothing would be worse; the weaker promise is stated on
        // ViewOnceRevealResponse rather than pretended away.
        val message = message(mediaId = null)
        stubOpenableBy(message, recipient)

        val reveal = service.markViewOnceViewed(message.id, recipient)

        verify(exactly = 0) { mediaObjects.takeAndDestroy(any()) }
        assertNull(reveal.mediaBytes)
        assertEquals("https://cdn.example/blob?sig=abc", reveal.mediaUrl)
        assertEquals("https://cdn.example/thumb?sig=abc", reveal.thumbnailUrl)
    }
}
