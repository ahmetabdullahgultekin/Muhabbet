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
    fun `should not write any lastseen key when going online`() {
        // Regression: the write-only lastseen Redis key (no TTL, never read) was removed.
        adapter.setOnline(userId, 60)

        verify(exactly = 0) { valueOps.set(match { it.startsWith("lastseen:") }, any()) }
    }

    @Test
    fun `should delete presence key and write nothing else when going offline`() {
        adapter.setOffline(userId)

        verify { redisTemplate.delete("presence:$userId") }
        verify(exactly = 0) { valueOps.set(any(), any()) }
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
