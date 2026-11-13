package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.LinkPreviewQuotaPort
import com.muhabbet.shared.config.LinkPreviewProperties
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * Per-user budget of outbound link-preview fetches, one INCR per fetch in a fixed one-minute bucket
 * (#916) — the same shape as `RedisOtpQuotaAdapter`, for the same reasons: shared across instances,
 * survives a deploy, atomic without a read-modify-write. A fixed window can admit up to twice the
 * limit across a boundary, which is fine for a throughput ceiling.
 *
 * **Fails open on a Redis outage.** The fetcher's bulkhead still caps concurrent fetches
 * instance-wide, so an outage loosens the per-user split but not the total; failing closed would
 * break previews for everyone whenever Redis blinks. Logged at ERROR so the loosening is visible.
 */
@Component
class RedisLinkPreviewQuotaAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val properties: LinkPreviewProperties,
) : LinkPreviewQuotaPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun tryConsume(userId: UUID): Boolean = try {
        val minute = System.currentTimeMillis() / MILLIS_PER_MINUTE
        val key = "linkpreview:quota:$userId:$minute"
        val count = redisTemplate.opsForValue().increment(key)
        if (count == 1L) redisTemplate.expire(key, BUCKET_TTL)
        val allowed = count == null || count <= properties.fetchesPerUserPerMinute
        if (!allowed) log.warn("Link preview quota exceeded for user {}: {} this minute", userId, count)
        allowed
    } catch (e: Exception) {
        log.error("Link preview quota check failed; allowing the fetch. Redis unreachable?", e)
        true
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        /** Outlives its minute so a slow clock edge cannot leave a bucket without expiry. */
        val BUCKET_TTL: Duration = Duration.ofMinutes(2)
    }
}
