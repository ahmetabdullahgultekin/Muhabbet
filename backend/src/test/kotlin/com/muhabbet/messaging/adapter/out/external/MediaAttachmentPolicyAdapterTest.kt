package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.media.domain.port.`in`.GetMediaUrlUseCase
import com.muhabbet.media.domain.port.`in`.MediaUrlResult
import com.muhabbet.shared.config.MediaProperties
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.util.UUID

/**
 * The rule itself (#679), against strings written to fool it.
 *
 * `MediaAttachmentPolicyTest` proves the send path obeys whatever this says; this proves what it
 * says is not trivially forgeable. Every case below is a URL that some parser somewhere reads as
 * pointing at our host and another reads as pointing at the attacker's — which is the entire family
 * of bugs an origin check has to survive, because the parser that ultimately matters is OkHttp's,
 * inside the recipient's app, not this one.
 */
class MediaAttachmentPolicyAdapterTest {

    private val getMediaUrlUseCase: GetMediaUrlUseCase = mockk()

    private fun adapter(publicEndpoint: String? = "https://cdn-muhabbet.example") =
        MediaAttachmentPolicyAdapter(
            getMediaUrlUseCase,
            MediaProperties(
                minio = MediaProperties.MinioProperties(
                    endpoint = "http://minio:9000",
                    publicEndpoint = publicEndpoint
                )
            )
        )

    @Test
    fun `should accept a URL our own media host serves`() {
        assertTrue(
            adapter().isAllowedOrigin(
                "https://cdn-muhabbet.example/muhabbet-media/images/a.jpg?X-Amz-Signature=abc"
            )
        )
    }

    @Test
    fun `should reject an address on somebody else's host`() {
        assertFalse(adapter().isAllowedOrigin("https://attacker.test/beacon.gif"))
    }

    @Test
    fun `should reject the tricks that make one string name two hosts`() {
        val policy = adapter()

        assertAll(
            // Userinfo: everything before the @ is a credential, so the host is attacker.test.
            { assertFalse(policy.isAllowedOrigin("https://cdn-muhabbet.example@attacker.test/x")) },
            { assertFalse(policy.isAllowedOrigin("https://cdn-muhabbet.example:8080@attacker.test/x")) },
            // A backslash is a host separator to a browser and a path character to java.net.URI.
            { assertFalse(policy.isAllowedOrigin("https://cdn-muhabbet.example\\.attacker.test/x")) },
            // Suffix, not origin: our name is a prefix of theirs.
            { assertFalse(policy.isAllowedOrigin("https://cdn-muhabbet.example.attacker.test/x")) },
            // Our host as a path or a parameter of theirs.
            { assertFalse(policy.isAllowedOrigin("https://attacker.test/https://cdn-muhabbet.example/x")) },
            { assertFalse(policy.isAllowedOrigin("https://attacker.test/?u=https://cdn-muhabbet.example/x")) },
            // Scheme downgrade and scheme swap on the right host.
            { assertFalse(policy.isAllowedOrigin("http://cdn-muhabbet.example/x")) },
            { assertFalse(policy.isAllowedOrigin("javascript:fetch('https://attacker.test')")) },
            { assertFalse(policy.isAllowedOrigin("data:image/gif;base64,R0lGODlhAQABAAAAACw=")) },
            // A newline is stripped by some parsers and kept by others; refuse rather than guess.
            { assertFalse(policy.isAllowedOrigin("https://cdn-muhabbet.example\n.attacker.test/x")) },
            { assertFalse(policy.isAllowedOrigin(" https://cdn-muhabbet.example/x")) },
            // The origin with nothing after it is not an object, and leaves no boundary to test.
            { assertFalse(policy.isAllowedOrigin("https://cdn-muhabbet.example")) },
            { assertFalse(policy.isAllowedOrigin("")) },
            // Unbounded strings do not get to become unbounded TEXT rows.
            { assertFalse(policy.isAllowedOrigin("https://cdn-muhabbet.example/" + "a".repeat(4096))) }
        )
    }

    @Test
    fun `should accept the GIPHY CDN the app's own picker sends from`() {
        // The GIF and sticker picker hands `WsMessage.SendMessage` a URL off GIPHY's CDN and never
        // re-hosts the file (`GifStickerPicker` → `ChatScreen.onGifSelected`). Without this the fix
        // would stop GIFs and stickers being sent at all — the exact silent breakage #679's
        // acceptance criteria warn about — so it is asserted rather than assumed.
        val policy = adapter()

        assertAll(
            { assertTrue(policy.isAllowedOrigin("https://media.giphy.com/media/abc/giphy.gif?cid=1")) },
            { assertTrue(policy.isAllowedOrigin("https://media3.giphy.com/media/abc/giphy.gif")) },
            { assertTrue(policy.isAllowedOrigin("https://stickers.giphy.com/media/abc/sticker.webp")) },
            // Allowing the CDN is not allowing anything that merely says giphy.
            { assertFalse(policy.isAllowedOrigin("https://media.giphy.com.attacker.test/x.gif")) },
            { assertFalse(policy.isAllowedOrigin("https://media.giphy.com@attacker.test/x.gif")) },
            { assertFalse(policy.isAllowedOrigin("https://attacker.test/media.giphy.com/x.gif")) }
        )
    }

    @Test
    fun `should refuse every third-party origin when the list is emptied`() {
        val policy = MediaAttachmentPolicyAdapter(
            getMediaUrlUseCase,
            MediaProperties(
                minio = MediaProperties.MinioProperties(publicEndpoint = "https://cdn-muhabbet.example"),
                attachmentOrigins = emptyList()
            )
        )

        assertAll(
            { assertTrue(policy.isAllowedOrigin("https://cdn-muhabbet.example/a.jpg")) },
            { assertFalse(policy.isAllowedOrigin("https://media.giphy.com/media/abc/giphy.gif")) }
        )
    }

    @Test
    fun `should fall back to the internal endpoint when no public one is configured`() {
        // A dev machine with no CDN in front of MinIO publishes the internal endpoint verbatim —
        // `MinioMediaStorageAdapter.getPresignedUrl` skips the rewrite — so that is what is ours.
        val policy = adapter(publicEndpoint = null)

        assertAll(
            { assertTrue(policy.isAllowedOrigin("http://minio:9000/muhabbet-media/a.jpg")) },
            { assertFalse(policy.isAllowedOrigin("https://cdn-muhabbet.example/a.jpg")) }
        )
    }

    @Test
    fun `should return the URLs the media module mints for the sender's own upload`() {
        val mediaId = UUID.randomUUID()
        val sender = UUID.randomUUID()
        every { getMediaUrlUseCase.getPresignedUrl(mediaId, sender) } returns
            MediaUrlResult(url = "https://cdn-muhabbet.example/a.jpg", thumbnailUrl = "https://cdn-muhabbet.example/t.jpg")

        val resolved = adapter().resolveOwnUpload(mediaId, sender)

        assertAll(
            { assertEquals("https://cdn-muhabbet.example/a.jpg", resolved?.mediaUrl) },
            { assertEquals("https://cdn-muhabbet.example/t.jpg", resolved?.thumbnailUrl) }
        )
    }

    @Test
    fun `should resolve nothing for a blob the sender does not own`() {
        val mediaId = UUID.randomUUID()
        val sender = UUID.randomUUID()
        every { getMediaUrlUseCase.getPresignedUrl(mediaId, sender) } throws
            BusinessException(ErrorCode.MEDIA_FORBIDDEN)

        // Null, not an exception: the caller's next move is the host test on whatever the client
        // sent, which is the same rule a forward lives under.
        assertNull(adapter().resolveOwnUpload(mediaId, sender))
    }

    @Test
    fun `should resolve nothing when storage will not answer`() {
        val mediaId = UUID.randomUUID()
        val sender = UUID.randomUUID()
        every { getMediaUrlUseCase.getPresignedUrl(mediaId, sender) } throws RuntimeException("MinIO down")

        // MinIO being unreachable must not become "this send fails". It degrades to the weaker rule,
        // which is still strong enough that no foreign address gets through.
        assertNull(adapter().resolveOwnUpload(mediaId, sender))
    }
}
