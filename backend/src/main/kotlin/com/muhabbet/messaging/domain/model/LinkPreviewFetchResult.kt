package com.muhabbet.messaging.domain.model

/**
 * What one attempt to fetch a link preview produced (#916).
 *
 * The split that matters is between a fact about the **URL** and a fact about **this instance**.
 * The first may be cached and shared with every user who sends the same link; the second must not
 * be. They used to be the same `null`, so a moment of local overload — every fetch slot taken — was
 * written to the shared cache as "this page has no preview" for everyone, for the failure TTL.
 */
sealed interface LinkPreviewFetchResult {

    /** The page was fetched and read. Cacheable for the success TTL. */
    data class Fetched(val preview: LinkPreview) : LinkPreviewFetchResult

    /**
     * The page cannot or may not be previewed: the host is internal, it did not answer in time, it
     * is not HTML, it answered non-2xx, or it redirected too often. A property of the URL, so it is
     * cacheable — for the shorter failure TTL, because some of these are transient on the far side.
     */
    data object Unavailable : LinkPreviewFetchResult

    /**
     * No fetch was attempted because this instance has no capacity right now. Says nothing about the
     * URL, so it is **never** cached, and the caller's budget is not charged for a fetch not made.
     */
    data object Busy : LinkPreviewFetchResult
}
