package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.PreKeyFetchQuotaPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Redis fixed-window budget for prekey bundle fetches (#749). Two ceilings, both of which must pass:
 *  - **hourly, per requester** — bounds how many victims one account can drain at once
 *  - **daily, per (requester, target) pair** — a legitimate client fetches a peer's bundle when it
 *    starts a session, a handful of times a day at most; a drain needs hundreds
 *
 * Same shape as the OTP quota: INCR, and EXPIRE on the first write in a bucket. Both counters are
 * consumed regardless, so a refused pair still spends requester budget.
 *
 * **Fails closed on a Redis outage.** A refused fetch only delays setting up an encrypted session;
 * an unbounded one lets the outage window be used to drain prekeys. Logged at ERROR.
 */
@Component
class RedisPreKeyFetchQuotaAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${muhabbet.encryption.prekey-fetch.max-per-hour-per-requester:60}")
    private val maxPerHourPerRequester: Int,
    @Value("\${muhabbet.encryption.prekey-fetch.max-per-day-per-pair:10}")
    private val maxPerDayPerPair: Int,
) : PreKeyFetchQuotaPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun tryConsume(requesterId: UUID, targetUserId: UUID): Boolean {
        val now = Instant.now()
        return try {
            val requesterOk = consume(
                key = "prekey:quota:requester:$requesterId:${HOUR.format(now)}",
                limit = maxPerHourPerRequester,
                ttl = Duration.ofHours(2),
            )
            val pairOk = consume(
                key = "prekey:quota:pair:$requesterId:$targetUserId:${DAY.format(now)}",
                limit = maxPerDayPerPair,
                ttl = Duration.ofDays(2),
            )
            if (!requesterOk || !pairOk) {
                log.warn(
                    "Prekey fetch quota exceeded: requester={}, target={}, requesterOk={}, pairOk={}",
                    requesterId, targetUserId, requesterOk, pairOk,
                )
            }
            requesterOk && pairOk
        } catch (e: Exception) {
            log.error("Prekey fetch quota check failed; refusing the fetch. Redis unreachable?", e)
            false
        }
    }

    private fun consume(key: String, limit: Int, ttl: Duration): Boolean {
        val count = redisTemplate.opsForValue().increment(key) ?: return false
        if (count == 1L) {
            redisTemplate.expire(key, ttl)
        }
        return count <= limit
    }

    private companion object {
        val HOUR: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'H'HH").withZone(ZoneOffset.UTC)
        val DAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
    }
}
