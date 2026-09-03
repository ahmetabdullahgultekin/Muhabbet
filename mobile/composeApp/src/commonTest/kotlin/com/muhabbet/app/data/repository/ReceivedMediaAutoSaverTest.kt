package com.muhabbet.app.data.repository

import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.protocol.WsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val ME = "11111111-1111-1111-1111-111111111111"
private const val THEM = "22222222-2222-2222-2222-222222222222"

/**
 * The decision half of media auto-save (#593).
 *
 * The doing half needs a network and a real device gallery, neither of which exists here; what is
 * worth pinning is which frames are eligible at all — and above all that a view-once photo is not,
 * since auto-saving one into a permanent album would defeat #541 before the recipient has even
 * opened the seal.
 */
class ReceivedMediaAutoSaverTest {

    private fun frame(
        senderId: String = THEM,
        contentType: ContentType = ContentType.IMAGE,
        mediaUrl: String? = "https://cdn.example/photo.jpg",
        viewOnce: Boolean = false,
        expiresAt: Long? = null
    ) = WsMessage.NewMessage(
        messageId = "m1",
        conversationId = "c1",
        senderId = senderId,
        senderName = "Someone",
        content = "",
        contentType = contentType,
        mediaUrl = mediaUrl,
        serverTimestamp = 0L,
        viewOnce = viewOnce,
        expiresAt = expiresAt
    )

    @Test
    fun `should auto-save when an incoming photo arrives and the setting is on`() {
        assertTrue(shouldAutoSaveMedia(frame(), ME, enabled = true))
    }

    @Test
    fun `should auto-save when an incoming video arrives`() {
        val message = frame(contentType = ContentType.VIDEO, mediaUrl = "https://cdn.example/clip.mp4")
        assertTrue(shouldAutoSaveMedia(message, ME, enabled = true))
    }

    @Test
    fun `should not auto-save when the setting is off`() {
        assertFalse(shouldAutoSaveMedia(frame(), ME, enabled = false))
    }

    @Test
    fun `should not auto-save when the media is view-once`() {
        // The one rule here that is a correctness requirement, not a preference: #541 destroys the
        // object server-side as it is revealed so a view-once photo cannot outlive its viewing, and
        // a copy in the camera roll would outlive it permanently.
        assertFalse(shouldAutoSaveMedia(frame(viewOnce = true), ME, enabled = true))
    }

    @Test
    fun `should not auto-save when the message disappears`() {
        val disappearing = frame(expiresAt = 1_700_000_000_000L)
        assertFalse(shouldAutoSaveMedia(disappearing, ME, enabled = true))
    }

    @Test
    fun `should not auto-save when the sender is this device`() {
        assertFalse(shouldAutoSaveMedia(frame(senderId = ME), ME, enabled = true))
    }

    @Test
    fun `should not auto-save when the content type is not photo or video`() {
        val notMedia = listOf(
            ContentType.TEXT, ContentType.VOICE, ContentType.DOCUMENT, ContentType.LOCATION,
            ContentType.CONTACT, ContentType.POLL, ContentType.STICKER, ContentType.GIF
        )
        notMedia.forEach { type ->
            assertFalse(
                shouldAutoSaveMedia(frame(contentType = type), ME, enabled = true),
                "auto-save must not apply to $type"
            )
        }
    }

    @Test
    fun `should not auto-save when the frame carries no media url`() {
        assertFalse(shouldAutoSaveMedia(frame(mediaUrl = null), ME, enabled = true))
        assertFalse(shouldAutoSaveMedia(frame(mediaUrl = "  "), ME, enabled = true))
    }

    @Test
    fun `should name the file after the message id so a repeat write is recognisable`() {
        assertEquals("muhabbet-m1.jpg", fileNameFor("m1", "image/jpeg"))
        assertEquals("muhabbet-m1.mp4", fileNameFor("m1", "video/mp4"))
    }

    @Test
    fun `should infer the mime type from the url extension and fall back on the content type`() {
        assertEquals("image/png", mimeTypeFor(ContentType.IMAGE, "https://cdn.example/a.png"))
        // Presigned URLs carry a query string; the extension is before it, not at the end.
        assertEquals(
            "image/jpeg",
            mimeTypeFor(ContentType.IMAGE, "https://cdn.example/a.jpg?X-Amz-Signature=abc")
        )
        assertEquals("video/mp4", mimeTypeFor(ContentType.VIDEO, "https://cdn.example/no-extension"))
        assertEquals("image/jpeg", mimeTypeFor(ContentType.IMAGE, "https://cdn.example/no-extension"))
    }
}
