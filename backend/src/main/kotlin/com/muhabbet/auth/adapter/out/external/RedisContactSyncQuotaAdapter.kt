package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.port.out.ContactSyncQuotaPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

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
 * **What Redis holds is not the address book.** The incoming hashes are unsalted SHA-256 of E.164
 * numbers, which anyone can reverse by walking the ~10^9 number space, so storing them would be a
 * recoverable copy of each user's contacts — non-users included — that did not exist before. Each
 * hash is instead replaced by a keyed digest, HMAC-SHA256 truncated to [DIGEST_BYTES] bytes, under a
 * key derived from the server's JWT secret with a purpose label (so the JWT key is never used
 * directly for anything else). A Redis dump is then not reversible without that key, and the
 * distinct-count semantics survive: at 96 bits a collision inside one account's 2000-entry day is
 * negligible. The set expires at the end of its own UTC day plus [EXPIRY_MARGIN_SECONDS], so nothing
 * outlives the day it was counted for. Rotating the JWT secret simply resets the day's counters.
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
    @Value("\${muhabbet.jwt.secret}")
    jwtSecret: String,
) : ContactSyncQuotaPort {

    private val log = LoggerFactory.getLogger(javaClass)

    private val digestKey = SecretKeySpec(hmac(jwtSecret.toByteArray(), DIGEST_KEY_LABEL), HMAC_ALGORITHM)

    override fun tryConsume(userId: UUID, phoneHashes: Set<String>): Boolean {
        if (phoneHashes.isEmpty()) return true
        val now = Instant.now()
        val key = "contacts:quota:$userId:${DAY.format(now)}"
        val expireAt = LocalDate.ofInstant(now, ZoneOffset.UTC).plusDays(1)
            .atStartOfDay(ZoneOffset.UTC).toEpochSecond() + EXPIRY_MARGIN_SECONDS
        return try {
            val digests = phoneHashes.mapTo(LinkedHashSet()) { digest(it) }
            val args = listOf(maxNewHashesPerDay.toString(), expireAt.toString()) + digests
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

    /** Keyed, truncated digest of one phone hash: what Redis stores in its place. */
    private fun digest(phoneHash: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(digestKey)
        return HEX.formatHex(mac.doFinal(phoneHash.toByteArray()), 0, DIGEST_BYTES)
    }

    private companion object {
        const val REFUSED = -1L

        const val HMAC_ALGORITHM = "HmacSHA256"

        /** 96 bits: collision-free in practice for a 2000-entry set, and a third of a raw hash. */
        const val DIGEST_BYTES = 12

        /** Purpose label for deriving the digest key from the JWT secret. Changing it resets counters. */
        val DIGEST_KEY_LABEL = "muhabbet/contact-sync-quota/v1".toByteArray()

        /** Kept a little past midnight so a sync straddling the boundary still sees its own set. */
        const val EXPIRY_MARGIN_SECONDS = 3600L

        val HEX: HexFormat = HexFormat.of()

        fun hmac(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
            return mac.doFinal(data)
        }

        val DAY: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

        // KEYS[1] = the account's set for today; ARGV[1] = limit; ARGV[2] = expiry as epoch
        // seconds (end of the UTC day plus margin); ARGV[3..] = distinct digests. Returns the new set size, or -1 when refused.
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
              redis.call('EXPIREAT', key, tonumber(ARGV[2]))
            end
            return size + fresh
            """.trimIndent(),
            // javaObjectType, not java: the latter is primitive `long`, which the script
            // executor cannot map a Redis integer reply onto.
            Long::class.javaObjectType,
        )
    }
}
