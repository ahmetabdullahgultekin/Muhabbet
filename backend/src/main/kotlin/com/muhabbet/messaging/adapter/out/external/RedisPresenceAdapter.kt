package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.port.out.PresencePort
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

@Component
class RedisPresenceAdapter(
    private val redisTemplate: StringRedisTemplate
) : PresencePort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun setOnline(userId: UUID, ttlSeconds: Long) {
        // Single TTL key is the whole online signal; it auto-expires so liveness is correct even if
        // a disconnect is never observed. Last-seen is persisted to PostgreSQL by the WS handler
        // (the durable source of truth) — see ChatWebSocketHandler.afterConnectionClosed.
        redisTemplate.opsForValue().set(presenceKey(userId), "1", Duration.ofSeconds(ttlSeconds))
    }

    override fun setOffline(userId: UUID) {
        redisTemplate.delete(presenceKey(userId))
    }

    override fun isOnline(userId: UUID): Boolean {
        return redisTemplate.hasKey(presenceKey(userId))
    }

    override fun getOnlineUserIds(userIds: Collection<UUID>): Set<UUID> {
        if (userIds.isEmpty()) return emptySet()
        val keys = userIds.map { presenceKey(it) }
        val results = redisTemplate.opsForValue().multiGet(keys) ?: return emptySet()
        return userIds.zip(results)
            .filter { (_, value) -> value != null }
            .map { (userId, _) -> userId }
            .toSet()
    }

    private fun presenceKey(userId: UUID) = "presence:$userId"
}
