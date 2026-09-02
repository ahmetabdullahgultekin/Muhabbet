package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.media.domain.port.`in`.GetMediaUrlUseCase
import com.muhabbet.messaging.domain.port.out.MediaAttachmentPolicyPort
import com.muhabbet.messaging.domain.port.out.ResolvedAttachment
import com.muhabbet.shared.config.MediaProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Answers [MediaAttachmentPolicyPort] from the media module and this deployment's media
 * configuration, alongside [MediaObjectAdapter] and for the same reason: this is the one place
 * messaging and media meet.
 *
 * Depends on media's **in-port**, never on `MediaFileRepository` or `MediaStoragePort`.
 * `GetMediaUrlUseCase` already is the ownership check plus the mint — it refuses anything the
 * caller did not upload — so resolving a send's media is the endpoint that already existed, asked
 * from inside instead of over HTTP.
 */
@Component
class MediaAttachmentPolicyAdapter(
    private val getMediaUrlUseCase: GetMediaUrlUseCase,
    mediaProperties: MediaProperties
) : MediaAttachmentPolicyPort {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * The origin the app publishes media on: the public endpoint where one is configured, and the
     * internal one otherwise, which is exactly the choice `MinioMediaStorageAdapter.getPresignedUrl`
     * makes when it rewrites a freshly signed URL. Both sides read the same property, so what this
     * accepts is by construction what the server itself produces.
     */
    private val ownOrigin: String =
        (mediaProperties.minio.publicEndpoint?.takeIf { it.isNotBlank() } ?: mediaProperties.minio.endpoint)
            .trim()
            .trimEnd('/')

    /**
     * Our own origin plus the third-party media origins the app itself sends from — GIPHY, for the
     * GIF and sticker picker, which hands the client a CDN URL and never re-hosts it. See
     * [MediaProperties.attachmentOrigins] for why a fixed third party does not reopen #679.
     */
    private val allowedOrigins: List<String> =
        (listOf(ownOrigin) + mediaProperties.attachmentOrigins.map { it.trim().trimEnd('/') })
            .filter { it.isNotEmpty() }
            .distinct()

    override fun resolveOwnUpload(mediaId: UUID, senderId: UUID): ResolvedAttachment? =
        try {
            val resolved = getMediaUrlUseCase.getPresignedUrl(mediaId, senderId)
            ResolvedAttachment(mediaUrl = resolved.url, thumbnailUrl = resolved.thumbnailUrl)
        } catch (e: Exception) {
            // Every reason to fail here means the same thing to the caller — this id buys the sender
            // nothing — so they collapse into null rather than into four branches. A rejection
            // (unknown id, someone else's blob) is the common case and is not worth a stack trace;
            // storage being unreachable is not, and is logged, because a send that quietly drops to
            // the weaker rule should leave a trace of why.
            log.debug("Media id {} not resolvable for sender {}: {}", mediaId, senderId, e.message)
            null
        }

    /**
     * A prefix test on purpose, not a parsed comparison of hosts.
     *
     * The recipient's phone does not run this parser: Coil hands the string to OkHttp, and the
     * whole family of URL bugs comes from two parsers disagreeing about where the authority ends.
     * `https://cdn.ours.example@evil.test/x` has host `evil.test`, `https://cdn.ours.example\.evil.test/`
     * is a host to a browser and a malformed URI to `java.net.URI`. Requiring the string to *begin*
     * with the exact origin and continue with `/` leaves no authority for anyone to disagree about:
     * after `https://cdn.ours.example/` every parser is reading a path.
     *
     * Control characters and whitespace are refused outright for the same reason — a stripped or
     * kept `\n` is another way for two parsers to read one string differently.
     */
    override fun isAllowedOrigin(url: String): Boolean {
        if (url.length > MAX_URL_LENGTH) return false
        if (url.any { it.isWhitespace() || it.isISOControl() }) return false
        return allowedOrigins.any { url.startsWith("$it/") }
    }

    companion object {
        /**
         * A presigned MinIO URL runs to a few hundred characters. The cap is far above that and is
         * here only so an unbounded `TEXT` column cannot be filled by a socket frame.
         */
        private const val MAX_URL_LENGTH = 2048
    }
}
