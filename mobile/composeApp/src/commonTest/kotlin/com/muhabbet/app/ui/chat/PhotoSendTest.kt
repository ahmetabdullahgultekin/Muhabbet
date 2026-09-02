package com.muhabbet.app.ui.chat

import com.muhabbet.shared.model.ContentType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The test that would have caught #515.
 *
 * The defect was not subtle once you could see it: the optimistic bubble was constructed with
 * `viewOnce = viewOnceEnabled` and the frame put on the socket was constructed without it. The
 * sender's screen said sealed, the server stored an ordinary message, and the recipient received the
 * photo in full and permanently. Nothing in the app compared the two descriptions of the same send,
 * and no test existed that could — the pair was assembled inline inside a composable, twice.
 *
 * So the pair is built by one function now, and this asserts they agree.
 */
class PhotoSendTest {

    private fun photo(viewOnce: Boolean) = outgoingPhoto(
        messageId = "msg-1",
        requestId = "req-1",
        conversationId = "conv-1",
        senderId = "user-1",
        caption = "Fotoğraf",
        mediaUrl = "https://cdn.example/blob.jpg",
        thumbnailUrl = "https://cdn.example/thumb.jpg",
        mediaId = "media-1",
        viewOnce = viewOnce,
        sentAt = Instant.fromEpochMilliseconds(1_700_000_000_000)
    )

    @Test
    fun should_put_view_once_on_the_wire_when_it_is_armed() {
        assertTrue(
            photo(viewOnce = true).frame.viewOnce,
            "an armed view-once photo must reach the server as view-once, or the recipient keeps it"
        )
    }

    @Test
    fun should_show_the_sender_the_same_thing_it_told_the_server() {
        val armed = photo(viewOnce = true)
        val ordinary = photo(viewOnce = false)

        assertEquals(armed.frame.viewOnce, armed.optimistic.viewOnce)
        assertEquals(ordinary.frame.viewOnce, ordinary.optimistic.viewOnce)
    }

    @Test
    fun should_leave_an_ordinary_photo_unsealed() {
        val ordinary = photo(viewOnce = false)

        assertFalse(ordinary.frame.viewOnce)
        assertFalse(ordinary.optimistic.viewOnce)
    }

    @Test
    fun should_carry_the_media_the_picker_produced() {
        val sent = photo(viewOnce = true)

        assertEquals(ContentType.IMAGE, sent.frame.contentType)
        assertEquals("https://cdn.example/blob.jpg", sent.frame.mediaUrl)
        assertEquals("https://cdn.example/thumb.jpg", sent.frame.thumbnailUrl)
        assertEquals("msg-1", sent.frame.messageId)
        assertEquals("req-1", sent.frame.requestId)
    }

    @Test
    fun should_name_the_uploaded_object_so_a_burn_can_destroy_it() {
        // Without this the server has no server-resolved reference to the blob, so burning a
        // view-once photo hides it and leaves the bytes in the bucket behind a presigned URL —
        // #541, the seven-day window. The URL is for rendering; this is what can be deleted.
        assertEquals("media-1", photo(viewOnce = true).frame.mediaId)
    }
}
