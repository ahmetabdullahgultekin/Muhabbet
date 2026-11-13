package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.LinkPreview

/**
 * Fetches a page from the public internet and reads its preview metadata.
 *
 * Implementations own every network safety property: they must never connect to an internal
 * address (including through DNS rebinding or a redirect), and must bound the time, bytes and
 * concurrency one call can consume. Returns null when the page could not or may not be fetched.
 */
interface LinkPreviewFetchPort {
    fun fetch(url: String): LinkPreview?
}
