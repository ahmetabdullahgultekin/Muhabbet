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
        val key = presenceKey(userId)
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(ttlSeconds))
        redisTemplate.opsForValue().set(lastSeenKey(userId), System.currentTimeMillis().toString())
    }

    override fun setOffline(userId: UUID) {
        redisTemplate.delete(presenceKey(userId))
        redisTemplate.opsForValue().set(lastSeenKey(userId), System.currentTimeMillis().toString())
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
    private fun lastSeenKey(userId: UUID) = "lastseen:$userId"
}
