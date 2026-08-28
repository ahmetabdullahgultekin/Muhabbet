package com.muhabbet.messaging.adapter.out

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.shared.model.ContentType as SharedContentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The live counterpart of `MessageMapperTest`, and for the same reason.
 *
 * This frame existed twice — once in `NoOpMessageBroadcaster`, once in `RedisMessageBroadcaster` —
 * with a comment in each saying the two were kept in step by hand. `@Primary` puts Redis in front
 * in production while most tests see the NoOp, so the two drifting apart is the worst kind of split:
 * green locally, wrong on the phone. Both now call one function and this asserts what it produces.
 */
class NewMessageFrameTest {

    private fun message(
        contentType: ContentType = ContentType.TEXT,
        viewOnce: Boolean = false,
        expiresAt: Instant? = null
    ) = Message(
        id = UUID.randomUUID(),
        conversationId = UUID.randomUUID(),
        senderId = UUID.randomUUID(),
        contentType = contentType,
        content = "merhaba",
        mediaUrl = "https://cdn.example/blob.jpg?X-Amz-Signature=deadbeef",
        thumbnailUrl = "https://cdn.example/thumb.jpg?X-Amz-Signature=deadbeef",
        viewOnce = viewOnce,
        expiresAt = expiresAt,
        clientTimestamp = Instant.now(),
        serverTimestamp = Instant.now()
    )

    /**
     * #513. Without the deadline on this frame a disappearing message that arrives while the chat
     * is open can never be removed on time — the recipient builds their bubble from here and from
     * nowhere else.
     */
    @Test
    fun `should carry the expiry deadline of a disappearing message`() {
        val deadline = Instant.now().plusSeconds(30)

        val frame = message(expiresAt = deadline).toNewMessageFrame("Ayşe")

        assertEquals(deadline.toEpochMilli(), frame.expiresAt)
    }

    @Test
    fun `should report no deadline for a message in a chat with no timer`() {
        assertNull(message().toNewMessageFrame("Ayşe").expiresAt)
    }

    @Test
    fun `should withhold the media url of a view-once message`() {
        val frame = message(contentType = ContentType.IMAGE, viewOnce = true).toNewMessageFrame(null)

        assertTrue(frame.viewOnce)
        assertNull(frame.mediaUrl)
        assertNull(frame.thumbnailUrl)
    }

    @Test
    fun `should carry the media url of an ordinary image`() {
        val domain = message(contentType = ContentType.IMAGE)

        val frame = domain.toNewMessageFrame(null)

        assertEquals(domain.mediaUrl, frame.mediaUrl)
        assertEquals(SharedContentType.IMAGE, frame.contentType)
    }
}
