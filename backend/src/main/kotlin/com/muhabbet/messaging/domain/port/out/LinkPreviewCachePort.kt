package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.LinkPreview
import java.time.Duration

/**
 * Previews already fetched, keyed by the exact URL that was requested.
 *
 * Shared across users on purpose: a link in a group chat is previewed by every member's phone, and
 * without a shared entry each render is another outbound fetch the server makes on someone's
 * behalf. A cache outage must degrade to a miss, never to an error.
 */
interface LinkPreviewCachePort {

    /** The cached preview for [url], or null on a miss. An empty preview is a hit. */
    fun get(url: String): LinkPreview?

    fun put(preview: LinkPreview, ttl: Duration)
}
