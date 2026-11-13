package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.LinkPreviewQuotaPort
import com.muhabbet.shared.config.LinkPreviewProperties
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * Per-user budget of outbound link-preview fetches, one INCR per fetch in a fixed one-minute bucket
 * (#916) — the same shape as `RedisOtpQuotaAdapter`, for the same reasons: shared across instances,
 * survives a deploy, atomic without a read-modify-write. A fixed window can admit up to twice the
 * limit across a boundary, which is fine for a throughput ceiling.
 *
 * The increment and the bucket's expiry are one Lua script, so a bucket can never exist without a
 * TTL: issued as two commands, a failure between them would have left that minute's key forever.
 *
 * A [refund] decrements the *current* bucket and never below zero. If the minute rolled over between
 * the charge and the refund, the charge was in a bucket that no longer constrains anything, and the
 * refund lands in a fresh one — at worst the user gets one extra fetch at a boundary the fixed
 * window already treats loosely.
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
        val count = redisTemplate.execute(CONSUME, listOf(bucketKey(userId)), BUCKET_TTL.toMillis().toString())
        val allowed = count == null || count <= properties.fetchesPerUserPerMinute
        if (!allowed) log.warn("Link preview quota exceeded for user {}: {} this minute", userId, count)
        allowed
    } catch (e: Exception) {
        log.error("Link preview quota check failed; allowing the fetch. Redis unreachable?", e)
        true
    }

    override fun refund(userId: UUID) {
        try {
            redisTemplate.execute(REFUND, listOf(bucketKey(userId)))
        } catch (e: Exception) {
            // Losing a refund costs the user one unit of a one-minute budget; not worth failing for.
            log.warn("Link preview quota refund failed for user {}", userId, e)
        }
    }

    private fun bucketKey(userId: UUID): String =
        "linkpreview:quota:$userId:${System.currentTimeMillis() / MILLIS_PER_MINUTE}"

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
        /** Outlives its minute so a slow clock edge cannot leave a bucket without expiry. */
        val BUCKET_TTL: Duration = Duration.ofMinutes(2)

        val CONSUME: RedisScript<Long> = RedisScript.of(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return count
            """.trimIndent(),
            Long::class.javaObjectType,
        )

        /** DECR keeps the key's TTL; GET-then-DECR is atomic inside the script. */
        val REFUND: RedisScript<Long> = RedisScript.of(
            """
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count > 0 then return redis.call('DECR', KEYS[1]) end
            return 0
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}
