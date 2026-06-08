package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PushNotificationContentTest {

    private fun message(contentType: ContentType, content: String): Message = Message(
        id = UUID.randomUUID(),
        conversationId = UUID.randomUUID(),
        senderId = UUID.randomUUID(),
        contentType = contentType,
        content = content,
        clientTimestamp = Instant.now()
    )

    @Test
    fun `should use plain content for text messages`() {
        val body = PushNotificationContent.bodyFor(message(ContentType.TEXT, "Merhaba"))

        assertEquals("Merhaba", body)
    }

    @Test
    fun `should not leak raw media content for image messages`() {
        // Regression: the prod (Redis) push path used to send message.content (a storage key / empty)
        // for media; it must show a placeholder instead.
        val body = PushNotificationContent.bodyFor(
            message(ContentType.IMAGE, "media/documents/abc/owner.jpg")
        )

        assertFalse(body.contains("media/"))
        assertEquals("📷 Fotoğraf", body)
    }

    @Test
    fun `should use placeholder for video voice and document`() {
        assertEquals("🎥 Video", PushNotificationContent.bodyFor(message(ContentType.VIDEO, "x")))
        assertEquals("🎙️ Sesli mesaj", PushNotificationContent.bodyFor(message(ContentType.VOICE, "x")))
        assertEquals("📄 Belge", PushNotificationContent.bodyFor(message(ContentType.DOCUMENT, "x")))
    }

    @Test
    fun `should truncate long text bodies to 100 chars`() {
        val longText = "a".repeat(250)
        val body = PushNotificationContent.bodyFor(message(ContentType.TEXT, longText))

        assertEquals(100, body.length)
    }
}
