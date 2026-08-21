package com.muhabbet.messaging.adapter.out.external

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.util.UUID

/**
 * Unit tests for the Redis presence adapter. [StringRedisTemplate] is mocked, so these pin the
 * exact key layout and TTL semantics the adapter writes — not that Redis honours them.
 *
 * The `lastseen:` assertions describe the adapter as it is today, deliberately and without
 * endorsement: the key is written on every online/offline transition, carries **no TTL**, and is
 * read by nothing in the tree (`lastSeenKey` is the only occurrence of the string outside these
 * tests). If that write is ever removed as dead, these two tests are the ones to delete with it.
 */
class RedisPresenceAdapterTest {

    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var valueOps: ValueOperations<String, String>
    private lateinit var adapter: RedisPresenceAdapter

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        redisTemplate = mockk(relaxed = true)
        valueOps = mockk(relaxed = true)
        every { redisTemplate.opsForValue() } returns valueOps
        adapter = RedisPresenceAdapter(redisTemplate)
    }

    @Test
    fun `should set presence key with TTL when going online`() {
        adapter.setOnline(userId, 90)

        verify { valueOps.set("presence:$userId", "1", Duration.ofSeconds(90)) }
    }

    @Test
    fun `should also stamp the untimed lastseen key when going online`() {
        adapter.setOnline(userId, 60)

        verify {
            valueOps.set(
                "lastseen:$userId",
                match<String> { it.toLongOrNull() != null }
            )
        }
    }

    @Test
    fun `should delete presence key and stamp lastseen when going offline`() {
        adapter.setOffline(userId)

        verify { redisTemplate.delete("presence:$userId") }
        verify {
            valueOps.set(
                "lastseen:$userId",
                match<String> { it.toLongOrNull() != null }
            )
        }
        // Going offline must never re-create the presence key.
        verify(exactly = 0) { valueOps.set("presence:$userId", any(), any<Duration>()) }
    }

    @Test
    fun `should report online when presence key exists`() {
        every { redisTemplate.hasKey("presence:$userId") } returns true

        assertTrue(adapter.isOnline(userId))
    }

    @Test
    fun `should report offline when presence key absent`() {
        every { redisTemplate.hasKey("presence:$userId") } returns false

        assertFalse(adapter.isOnline(userId))
    }

    @Test
    fun `should return empty set without touching Redis when no userIds given`() {
        val result = adapter.getOnlineUserIds(emptyList())

        assertTrue(result.isEmpty())
        verify(exactly = 0) { valueOps.multiGet(any()) }
    }

    @Test
    fun `should return only the userIds whose presence value is non-null`() {
        val online = UUID.randomUUID()
        val offline = UUID.randomUUID()
        every { valueOps.multiGet(listOf("presence:$online", "presence:$offline")) } returns
            listOf("1", null)

        val result = adapter.getOnlineUserIds(listOf(online, offline))

        assertEquals(setOf(online), result)
    }

    @Test
    fun `should return empty set when multiGet returns null`() {
        every { valueOps.multiGet(any()) } returns null

        val result = adapter.getOnlineUserIds(listOf(userId))

        assertTrue(result.isEmpty())
    }
}
