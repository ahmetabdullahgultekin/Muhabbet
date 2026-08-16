package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.OtpQuotaPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Redis-backed budget for started verifications (#440).
 *
 * Two independent ceilings, both of which must pass:
 *  - **hourly, deployment-wide** — the blast radius of anything automated
 *  - **daily, per number** — the 60-second cooldown alone allows 1,440 verifications against one
 *    number in a day, and no real person needs more than a handful
 *
 * Counters are keyed by wall-clock bucket rather than a sliding window, so the whole thing is one
 * INCR and one EXPIRE. A fixed window can allow up to twice the limit across a boundary; that is
 * acceptable for a cost ceiling and not acceptable for the attempt limit, which is why the attempt
 * limit is an atomic conditional UPDATE in the database instead.
 *
 * **Fails open on a Redis outage.** Losing Redis would otherwise mean nobody can log in at all,
 * which is a worse failure than an uncapped hour — and the per-IP throttle and per-number cooldown
 * are both still in force. The exception is logged at ERROR so an outage is visible rather than
 * silently removing a control.
 */
@Component
class RedisOtpQuotaAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${muhabbet.otp.max-per-hour-global:500}")
    private val maxPerHourGlobal: Int,
    @Value("\${muhabbet.otp.max-per-day-per-number:10}")
    private val maxPerDayPerNumber: Int,
) : OtpQuotaPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun tryConsume(phoneNumber: String): Boolean {
        val now = Instant.now()
        return try {
            val globalOk = consume(
                key = "otp:quota:global:${HOUR.format(now)}",
                limit = maxPerHourGlobal,
                ttl = Duration.ofHours(2),
                label = "global hourly",
            )
            // Consume the per-number budget regardless, so a number cannot be retried for free
            // during an hour that is already over its global ceiling.
            val numberOk = consume(
                key = "otp:quota:number:$phoneNumber:${DAY.format(now)}",
                limit = maxPerDayPerNumber,
                ttl = Duration.ofDays(2),
                label = "per-number daily",
            )
            globalOk && numberOk
        } catch (e: Exception) {
            log.error("OTP quota check failed; allowing the request. Redis unreachable?", e)
            true
        }
    }

    private fun consume(key: String, limit: Int, ttl: Duration, label: String): Boolean {
        val count = redisTemplate.opsForValue().increment(key) ?: return true
        if (count == 1L) {
            // Only the first write in a bucket sets the expiry, so a busy bucket cannot keep
            // pushing its own deadline out and become immortal.
            redisTemplate.expire(key, ttl)
        }
        if (count > limit) {
            log.warn("OTP {} quota exceeded: {} of {} on key {}", label, count, limit, key)
            return false
        }
        return true
    }

    private companion object {
        val HOUR: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'H'HH").withZone(ZoneOffset.UTC)
        val DAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
    }
}
