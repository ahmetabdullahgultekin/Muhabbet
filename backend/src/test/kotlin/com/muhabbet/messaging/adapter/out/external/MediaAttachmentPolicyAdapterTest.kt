package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.media.domain.port.`in`.ResolveMediaOwnerUseCase
import com.muhabbet.messaging.domain.port.out.MediaAttachment
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The half of the #679 rule that turns a URL into a claim about where it came from.
 * [com.muhabbet.messaging.domain.service.MediaAttachmentTest] covers what the services then do
 * with that claim.
 */
class MediaAttachmentPolicyAdapterTest {

    private val resolveMediaOwner = mockk<ResolveMediaOwnerUseCase>()
    private val adapter = MediaAttachmentPolicyAdapter(resolveMediaOwner)

    private val uploader = UUID.randomUUID()

    @Test
    fun `should report the uploader for a URL the media module recognises`() {
        val url = "https://cdn-muhabbet.example.test/muhabbet-media/images/u1/photo.jpg"
        every { resolveMediaOwner.findUploaderByUrl(url) } returns uploader

        assertEquals(MediaAttachment.OwnStorage(uploader), adapter.classify(url))
    }

    @Test
    fun `should report the sticker host for a Giphy URL`() {
        val url = "https://media3.giphy.com/media/abc123/giphy.gif?cid=1"
        every { resolveMediaOwner.findUploaderByUrl(url) } returns null

        assertEquals(MediaAttachment.PublicStickerHost, adapter.classify(url))
    }

    @Test
    fun `should report the sticker host for the apex domain`() {
        val url = "https://giphy.com/media/abc123/giphy.gif"
        every { resolveMediaOwner.findUploaderByUrl(url) } returns null

        assertEquals(MediaAttachment.PublicStickerHost, adapter.classify(url))
    }

    /**
     * The dot boundary is the whole point. A suffix test without it would accept any host someone
     * can register that happens to end in those characters.
     */
    @Test
    fun `should not accept a lookalike of the sticker host`() {
        val url = "https://evilgiphy.com/media/abc123/giphy.gif"
        every { resolveMediaOwner.findUploaderByUrl(url) } returns null

        assertEquals(MediaAttachment.Unrecognised, adapter.classify(url))
    }

    @Test
    fun `should not accept a host that only contains the sticker host as a prefix`() {
        val url = "https://giphy.com.attacker.example/beacon.gif"
        every { resolveMediaOwner.findUploaderByUrl(url) } returns null

        assertEquals(MediaAttachment.Unrecognised, adapter.classify(url))
    }

    @Test
    fun `should not accept the sticker host over plain HTTP`() {
        val url = "http://media.giphy.com/media/abc123/giphy.gif"
        every { resolveMediaOwner.findUploaderByUrl(url) } returns null

        assertEquals(MediaAttachment.Unrecognised, adapter.classify(url))
    }

    @Test
    fun `should report unrecognised for an arbitrary address`() {
        val url = "https://tracker.attacker.example/beacon.png"
        every { resolveMediaOwner.findUploaderByUrl(url) } returns null

        assertEquals(MediaAttachment.Unrecognised, adapter.classify(url))
    }

    /** A scheme with no host at all, which is what a `data:` or `javascript:` payload looks like. */
    @Test
    fun `should report unrecognised for a URL with no host`() {
        val url = "data:text/html;base64,PHNjcmlwdD4="
        every { resolveMediaOwner.findUploaderByUrl(url) } returns null

        assertEquals(MediaAttachment.Unrecognised, adapter.classify(url))
    }

    @Test
    fun `should report unrecognised for a string that is not a URL`() {
        val url = "not a url at all"
        every { resolveMediaOwner.findUploaderByUrl(url) } returns null

        assertEquals(MediaAttachment.Unrecognised, adapter.classify(url))
    }
}
