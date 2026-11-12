package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.PreKeyFetchQuotaPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
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
 * One Lua script checks both counters and, only if both have room, increments both and sets their
 * expiry — atomically, the same way [com.muhabbet.auth.adapter.out.external.RedisContactSyncQuotaAdapter]
 * does. Atomic matters twice: a crash between INCR and EXPIRE can no longer leave a counter with no
 * TTL, and a fetch refused by one ceiling no longer spends the other.
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
        val keys = listOf(
            "prekey:quota:requester:$requesterId:${HOUR.format(now)}",
            "prekey:quota:pair:$requesterId:$targetUserId:${DAY.format(now)}",
        )
        return try {
            val result = redisTemplate.execute(
                SCRIPT,
                keys,
                maxPerHourPerRequester.toString(),
                maxPerDayPerPair.toString(),
                REQUESTER_TTL.seconds.toString(),
                PAIR_TTL.seconds.toString(),
            )
            if (result != ALLOWED) {
                log.warn(
                    "Prekey fetch quota exceeded: requester={}, target={}, refusedBy={}",
                    requesterId, targetUserId, if (result == REQUESTER_REFUSED) "requester" else "pair",
                )
            }
            result == ALLOWED
        } catch (e: Exception) {
            log.error("Prekey fetch quota check failed; refusing the fetch. Redis unreachable?", e)
            false
        }
    }

    private companion object {
        const val ALLOWED = 1L
        const val REQUESTER_REFUSED = -1L

        /** Each bucket outlives its own window by one more window, then is reclaimed. */
        val REQUESTER_TTL: Duration = Duration.ofHours(2)
        val PAIR_TTL: Duration = Duration.ofDays(2)

        val HOUR: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'H'HH").withZone(ZoneOffset.UTC)
        val DAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

        // KEYS[1] = requester hourly counter, KEYS[2] = pair daily counter;
        // ARGV[1] = requester limit, ARGV[2] = pair limit, ARGV[3]/ARGV[4] = their TTLs in seconds.
        // Returns 1 when allowed (both incremented), -1 / -2 when the requester / pair ceiling refuses.
        val SCRIPT = DefaultRedisScript(
            """
            local requester = tonumber(redis.call('GET', KEYS[1]) or '0')
            local pair = tonumber(redis.call('GET', KEYS[2]) or '0')
            if requester >= tonumber(ARGV[1]) then return -1 end
            if pair >= tonumber(ARGV[2]) then return -2 end
            if redis.call('INCR', KEYS[1]) == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[3])) end
            if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4])) end
            return 1
            """.trimIndent(),
            // javaObjectType: the script executor cannot map a Redis integer onto primitive `long`.
            Long::class.javaObjectType,
        )
    }
}
