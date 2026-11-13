package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.LinkPreviewFetchResult

/**
 * Fetches a page from the public internet and reads its preview metadata.
 *
 * Implementations own every network safety property: they must never connect to an internal
 * address (including through DNS rebinding or a redirect), and must bound the time, bytes and
 * concurrency one call can consume.
 *
 * They must also keep the two kinds of "no preview" apart: [LinkPreviewFetchResult.Unavailable] is
 * a statement about the URL and will be cached for every user, [LinkPreviewFetchResult.Busy] is a
 * statement about this instance and will not. Reporting overload as `Unavailable` turns a burst of
 * traffic into ten minutes of missing previews for everyone.
 */
interface LinkPreviewFetchPort {
    fun fetch(url: String): LinkPreviewFetchResult
}
