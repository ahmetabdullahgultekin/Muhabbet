package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.media.domain.port.`in`.ResolveMediaOwnerUseCase
import com.muhabbet.messaging.domain.port.out.MediaAttachment
import com.muhabbet.messaging.domain.port.out.MediaAttachmentPolicyPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Bridges messaging's [MediaAttachmentPolicyPort] to the media module, mirroring
 * [ModerationBlockPolicyAdapter] and [AuthReadReceiptPolicyAdapter].
 *
 * Depends on media's **in-port**, not on `MediaFileRepository`: a use-case interface is the
 * published face of a module, a repository is its private plumbing.
 *
 * Ownership is asked first and answers most traffic — every photo, voice note and document the app
 * sends is a blob we stored. The sticker host is the one deliberate exception, below.
 */
@Component
class MediaAttachmentPolicyAdapter(
    private val resolveMediaOwner: ResolveMediaOwnerUseCase
) : MediaAttachmentPolicyPort {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        /**
         * GIFs and stickers are not ours and never were: `GifStickerPicker` searches Giphy and
         * hands the chat screen a `media*.giphy.com` address, which the recipient's image loader
         * fetches directly. An ownership-only rule would have quietly deleted a shipped feature —
         * it would have compiled, and every ownership test would still have passed.
         *
         * A closed list of one host, matched on a dot boundary, is not the bypassable "does the URL
         * look right" test #679 warns about: the sender cannot choose the host, so the address
         * cannot be one that logs the recipient's IP for them. It is hardcoded rather than
         * configurable because the client compiles the same host in — a deployment could not
         * meaningfully set it to anything else.
         */
        private const val STICKER_HOST = "giphy.com"
    }

    override fun classify(url: String): MediaAttachment {
        resolveMediaOwner.findUploaderByUrl(url)?.let { return MediaAttachment.OwnStorage(it) }
        if (isStickerHost(url)) return MediaAttachment.PublicStickerHost

        // Logged here rather than in the service because this is the only layer still holding the
        // string, and the address someone tried to make other people's phones fetch is the fact
        // worth having when a probe shows up. Truncated and stripped of control characters — it is
        // attacker-chosen text on its way into a log file.
        log.warn(
            "Media attachment refused, address is not ours and not the sticker host: {}",
            url.take(200).filter { !it.isISOControl() }
        )
        return MediaAttachment.Unrecognised
    }

    private fun isStickerHost(url: String): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            return false
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host ?: return false
        return host.equals(STICKER_HOST, ignoreCase = true) ||
            host.endsWith(".$STICKER_HOST", ignoreCase = true)
    }
}
