package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.ContactSyncQuotaPort
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
 * Redis-backed daily budget of distinct phone hashes per account (#748).
 *
 * One Redis set per account per UTC day holds the hashes already counted. A Lua script does the
 * whole check-and-add atomically: it counts how many of the incoming hashes are not yet in the set,
 * refuses — recording nothing — if they would take the set past the limit, and otherwise adds them.
 * Doing it in one script matters because two parallel syncs from one account would otherwise both
 * read "under the limit" and both add.
 *
 * Fixed window keyed by wall-clock day, the same trade as [RedisOtpQuotaAdapter]: an account can
 * spend up to twice the limit across midnight UTC, which still bounds enumeration to a few thousand
 * numbers a day instead of a million.
 *
 * **Fails closed on a Redis outage**, unlike the OTP quota. Refusing an OTP locks everyone out of
 * the app; refusing a contact sync leaves the contact list as it was. The control exists to stop
 * enumeration, and an outage is not a reason to stop stopping it. Logged at ERROR so it is visible.
 */
@Component
class RedisContactSyncQuotaAdapter(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${muhabbet.contact-sync.max-new-hashes-per-day:2000}")
    private val maxNewHashesPerDay: Int,
) : ContactSyncQuotaPort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun tryConsume(userId: UUID, phoneHashes: Set<String>): Boolean {
        if (phoneHashes.isEmpty()) return true
        val key = "contacts:quota:$userId:${DAY.format(Instant.now())}"
        val args = listOf(maxNewHashesPerDay.toString(), TTL.seconds.toString()) + phoneHashes
        return try {
            val result = redisTemplate.execute(SCRIPT, listOf(key), *args.toTypedArray())
            if (result == REFUSED) {
                log.warn("Contact sync daily quota exceeded: userId={}, limit={}", userId, maxNewHashesPerDay)
                false
            } else {
                true
            }
        } catch (e: Exception) {
            log.error("Contact sync quota check failed; refusing the sync. Redis unreachable?", e)
            false
        }
    }

    private companion object {
        const val REFUSED = -1L

        /** Two days, so a set outlives its own day by a full day and is then reclaimed. */
        val TTL: Duration = Duration.ofDays(2)

        val DAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

        // KEYS[1] = the account's set for today; ARGV[1] = limit; ARGV[2] = ttl seconds;
        // ARGV[3..] = distinct hashes. Returns the new set size, or -1 when refused.
        val SCRIPT = DefaultRedisScript(
            """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local fresh = 0
            for i = 3, #ARGV do
              if redis.call('SISMEMBER', key, ARGV[i]) == 0 then fresh = fresh + 1 end
            end
            local size = redis.call('SCARD', key)
            if size + fresh > limit then return -1 end
            if fresh > 0 then
              for i = 3, #ARGV do redis.call('SADD', key, ARGV[i]) end
              if redis.call('TTL', key) < 0 then redis.call('EXPIRE', key, tonumber(ARGV[2])) end
            end
            return size + fresh
            """.trimIndent(),
            // javaObjectType, not java: the latter is primitive `long`, which the script
            // executor cannot map a Redis integer reply onto.
            Long::class.javaObjectType,
        )
    }
}
