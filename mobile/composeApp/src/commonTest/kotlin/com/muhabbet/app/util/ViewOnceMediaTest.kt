package com.muhabbet.app.util

import com.muhabbet.shared.dto.ViewOnceRevealResponse
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/**
 * The client half of #541.
 *
 * The burn response no longer carries a URL for anything sent since V24 — the object is deleted
 * before the response is written — so if the client could not turn the encoded bytes back into a
 * photo, the recipient would spend their one look on a blank screen.
 */
class ViewOnceMediaTest {

    private fun reveal(base64: String?) = ViewOnceRevealResponse(
        messageId = "msg-1",
        mediaBase64 = base64,
        mediaContentType = "image/jpeg",
        viewedAt = 1_700_000_000_000
    )

    @Test
    fun should_decode_the_bytes_the_burn_released() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        assertContentEquals(bytes, reveal(Base64.encode(bytes)).decodedMedia())
    }

    @Test
    fun should_have_nothing_to_decode_for_a_message_sent_before_media_references_existed() {
        // Those fall back to `mediaUrl`, which is the caller's second branch.
        assertNull(reveal(null).decodedMedia())
    }

    @Test
    fun should_degrade_to_nothing_rather_than_throw_on_a_malformed_payload() {
        // The message is spent either way; taking the chat down over one unparseable field is a
        // worse answer than the failure snackbar.
        assertNull(reveal("not base64 !!!").decodedMedia())
    }
}
