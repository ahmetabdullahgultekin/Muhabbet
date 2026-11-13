package com.muhabbet.shared.security

import com.muhabbet.shared.config.MediaProperties
import org.springframework.stereotype.Component

/**
 * Whether a URL that other people's phones will fetch sits on an origin this deployment publishes
 * media from: our own media host, plus the closed list in [MediaProperties.attachmentOrigins].
 *
 * This is the #679 rule, and it applies to every URL one user can make another user's device load
 * — message media, statuses, and profile and group avatars, which every contact, co-member and
 * co-subscriber's app fetches unprompted. What makes such a URL acceptable is not that it is
 * harmless to fetch (that is [SsrfGuard]'s question, for the server's own fetches) but that the
 * person supplying it did not get to choose the server the viewer talks to.
 */
@Component
class MediaOriginGuard(mediaProperties: MediaProperties) {

    /**
     * Our own public media origin — the same choice `MinioMediaStorageAdapter` makes when it
     * rewrites a presigned URL, so what this accepts is what the server itself hands out.
     */
    private val allowedOrigins: List<String> =
        (
            listOf(mediaProperties.minio.publicEndpoint?.takeIf { it.isNotBlank() } ?: mediaProperties.minio.endpoint) +
                mediaProperties.attachmentOrigins
            )
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotEmpty() }
            .distinct()

    fun isAllowed(url: String): Boolean {
        if (url.length > MAX_URL_LENGTH) return false
        if (url.any { it.isWhitespace() || it.isISOControl() }) return false
        return allowedOrigins.any { url.startsWith("$it/") }
    }

    private companion object {
        /** A presigned MinIO URL runs to a few hundred characters; this only bounds a `TEXT` column. */
        const val MAX_URL_LENGTH = 2048
    }
}
