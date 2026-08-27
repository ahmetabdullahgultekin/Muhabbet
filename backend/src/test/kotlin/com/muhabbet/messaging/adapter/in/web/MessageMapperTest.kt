package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.shared.model.MessageStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * Covers the single place every REST reader of a message goes through.
 *
 * These are regression tests for #515, where the mapper dropped `viewOnce` on the floor and carried
 * `mediaUrl` regardless. Between them those two omissions meant no response the server produced ever
 * said a message was view-once, and every response that mentioned one shipped a working, presigned,
 * credential-free URL for the photo it was supposed to be sealing.
 */
class MessageMapperTest {

    private fun viewOnceImage(viewedAt: Instant? = null) = Message(
        id = UUID.randomUUID(),
        conversationId = UUID.randomUUID(),
        senderId = UUID.randomUUID(),
        contentType = ContentType.IMAGE,
        content = "Photo",
        mediaUrl = "https://cdn.example/muhabbet-media/blob.jpg?X-Amz-Signature=deadbeef",
        thumbnailUrl = "https://cdn.example/muhabbet-media/thumb.jpg?X-Amz-Signature=deadbeef",
        viewOnce = true,
        viewedAt = viewedAt,
        clientTimestamp = Instant.now()
    )

    @Test
    fun `should carry the view-once flag so a client can render the seal`() {
        val shared = viewOnceImage().toSharedMessage(MessageStatus.DELIVERED)

        assertTrue(shared.viewOnce)
    }

    @Test
    fun `should withhold the media url of a view-once message`() {
        val shared = viewOnceImage().toSharedMessage(MessageStatus.DELIVERED)

        assertNull(shared.mediaUrl)
        assertNull(shared.thumbnailUrl)
    }

    @Test
    fun `should report a burned view-once message as already viewed`() {
        val shared = viewOnceImage(viewedAt = Instant.now()).toSharedMessage(MessageStatus.READ)

        assertTrue(shared.viewOnceViewed)
    }

    @Test
    fun `should report an unopened view-once message as not yet viewed`() {
        val shared = viewOnceImage().toSharedMessage(MessageStatus.DELIVERED)

        assertFalse(shared.viewOnceViewed)
    }

    /**
     * #513: `expires_at` was written at send time and read by nothing except the cleanup job, so no
     * response the server produced ever told a client when a disappearing message was due. The app
     * could only discover it by reloading and noticing the message gone — which is why an expired
     * message stayed on screen for as long as the chat stayed open.
     */
    @Test
    fun `should carry the expiry deadline so a client can remove the message on time`() {
        val deadline = Instant.now().plusSeconds(30)
        val disappearing = viewOnceImage().copy(viewOnce = false, expiresAt = deadline)

        val shared = disappearing.toSharedMessage(MessageStatus.DELIVERED)

        assertEquals(deadline.toEpochMilli(), shared.expiresAt?.toEpochMilliseconds())
    }

    @Test
    fun `should report no deadline for a message in a chat with no timer`() {
        val shared = viewOnceImage().copy(viewOnce = false, expiresAt = null).toSharedMessage(MessageStatus.SENT)

        assertNull(shared.expiresAt)
    }

    @Test
    fun `should leave an ordinary image untouched`() {
        val ordinary = viewOnceImage().copy(viewOnce = false)

        val shared = ordinary.toSharedMessage(MessageStatus.DELIVERED)

        assertFalse(shared.viewOnce)
        assertEquals(ordinary.mediaUrl, shared.mediaUrl)
        assertEquals(ordinary.thumbnailUrl, shared.thumbnailUrl)
    }
}
