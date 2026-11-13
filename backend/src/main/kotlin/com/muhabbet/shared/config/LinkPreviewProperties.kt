package com.muhabbet.shared.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Every bound on the link-preview fetcher (#916), in one place.
 *
 * The endpoint makes the server fetch a URL of the caller's choosing, so each of these is a limit on
 * what one request — and one user — can make the server do: how long, how many bytes, how many hops,
 * how many at once, and how often.
 */
@ConfigurationProperties("muhabbet.link-preview")
data class LinkPreviewProperties(
    /** Wall-clock budget for the whole fetch, every redirect hop included. */
    val totalTimeout: Duration = Duration.ofSeconds(4),
    /** Per-connection TCP connect budget; the total still applies on top. */
    val connectTimeout: Duration = Duration.ofSeconds(2),
    /** Bytes of the final response read before parsing stops. The `<head>` is all a preview needs. */
    val maxBodyBytes: Long = 512L * 1024,
    val maxRedirects: Int = 5,
    /** Fetches in flight across the instance. Beyond this a request gets an empty preview at once. */
    val maxConcurrentFetches: Int = 8,
    /** Outbound fetches (cache misses) one user may cause per minute. */
    val fetchesPerUserPerMinute: Int = 30,
    val cacheTtl: Duration = Duration.ofHours(6),
    /** Shorter, so a page that was briefly down gets another chance the same day. */
    val failureCacheTtl: Duration = Duration.ofMinutes(10),
)
