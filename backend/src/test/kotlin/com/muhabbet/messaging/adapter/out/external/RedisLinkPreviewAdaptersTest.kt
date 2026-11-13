package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.model.LinkPreview
import com.muhabbet.messaging.domain.model.LinkPreviewFetchResult
import com.muhabbet.messaging.domain.port.out.LinkPreviewFetchPort
import com.muhabbet.messaging.domain.service.LinkPreviewService
import com.muhabbet.shared.config.LinkPreviewProperties
import com.redis.testcontainers.RedisContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * The link-preview cache and per-user budget against a real Redis (#916): that entries round-trip,
 * expire, and are shared; that the budget is atomic and per user. A mock would show none of that.
 */
@Testcontainers
class RedisLinkPreviewAdaptersTest {

    companion object {
        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7-alpine")
    }

    private val template: StringRedisTemplate by lazy {
        val factory = LettuceConnectionFactory(redis.redisHost, redis.redisPort)
        factory.afterPropertiesSet()
        StringRedisTemplate(factory)
    }

    private fun uniqueUrl() = "https://example.com/${UUID.randomUUID()}?q=ç"

    @Test
    fun `a cached preview round-trips with every field`() {
        val cache = RedisLinkPreviewCacheAdapter(template)
        val preview = LinkPreview(uniqueUrl(), "Başlık", "Açıklama", "https://example.com/i.png", "Site")

        cache.put(preview, Duration.ofMinutes(1))

        assertEquals(preview, cache.get(preview.url))
    }

    @Test
    fun `an empty preview is a cache hit, not a miss`() {
        val cache = RedisLinkPreviewCacheAdapter(template)
        val empty = LinkPreview(uniqueUrl())

        cache.put(empty, Duration.ofMinutes(1))

        assertEquals(empty, cache.get(empty.url))
    }

    @Test
    fun `an entry expires after its ttl`() {
        val cache = RedisLinkPreviewCacheAdapter(template)
        val preview = LinkPreview(uniqueUrl(), title = "short-lived")

        cache.put(preview, Duration.ofSeconds(1))
        Thread.sleep(2_100)

        assertNull(cache.get(preview.url))
    }

    @Test
    fun `the key is a hash, so the raw url is not readable from the key space`() {
        val cache = RedisLinkPreviewCacheAdapter(template)
        val url = uniqueUrl()
        cache.put(LinkPreview(url, title = "t"), Duration.ofMinutes(1))

        val keys = template.keys("linkpreview:v1:*")
        assertTrue(keys.isNotEmpty())
        assertTrue(keys.none { it.contains("example.com") })
    }

    @Test
    fun `a second identical request through the real cache makes no outbound call`() {
        val fetches = AtomicInteger()
        val url = uniqueUrl()
        val fetcher = object : LinkPreviewFetchPort {
            override fun fetch(url: String): LinkPreviewFetchResult {
                fetches.incrementAndGet()
                return LinkPreviewFetchResult.Fetched(LinkPreview(url, title = "fetched"))
            }
        }
        val properties = LinkPreviewProperties(fetchesPerUserPerMinute = 100)
        val service = LinkPreviewService(
            fetcher = fetcher,
            cache = RedisLinkPreviewCacheAdapter(template),
            quota = RedisLinkPreviewQuotaAdapter(template, properties),
            successTtl = properties.cacheTtl,
            failureTtl = properties.failureCacheTtl,
        )

        service.getLinkPreview(UUID.randomUUID(), url)
        val second = service.getLinkPreview(UUID.randomUUID(), url)

        assertEquals("fetched", second.title)
        assertEquals(1, fetches.get())
    }

    @Test
    fun `a user is allowed up to the per-minute budget and then refused, without affecting others`() {
        val quota = RedisLinkPreviewQuotaAdapter(template, LinkPreviewProperties(fetchesPerUserPerMinute = 3))
        val user = UUID.randomUUID()
        val other = UUID.randomUUID()

        // Avoid straddling a minute boundary, which would legitimately reset the bucket mid-test.
        val secondOfMinute = (System.currentTimeMillis() / 1000) % 60
        if (secondOfMinute >= 58) Thread.sleep(3_000)

        repeat(3) { i -> assertTrue(quota.tryConsume(user), "fetch ${i + 1} should be allowed") }
        assertFalse(quota.tryConsume(user), "the fourth fetch in a minute exceeds the budget")
        assertTrue(quota.tryConsume(other), "one user's spent budget must not block another")
    }

    @Test
    fun `the budget bucket carries an expiry so it cannot outlive its window`() {
        val quota = RedisLinkPreviewQuotaAdapter(template, LinkPreviewProperties())
        val user = UUID.randomUUID()

        quota.tryConsume(user)

        val key = template.keys("linkpreview:quota:$user:*").single()
        val ttl = template.getExpire(key)
        assertTrue(ttl in 1..120, "expected a TTL of at most two minutes, was $ttl")
    }

    @Test
    fun `a refund returns one unit of the budget`() {
        val quota = RedisLinkPreviewQuotaAdapter(template, LinkPreviewProperties(fetchesPerUserPerMinute = 2))
        val user = UUID.randomUUID()
        avoidMinuteBoundary()

        assertTrue(quota.tryConsume(user))
        assertTrue(quota.tryConsume(user))
        quota.refund(user)

        assertTrue(quota.tryConsume(user), "the refunded unit is available again")
        assertFalse(quota.tryConsume(user))
    }

    @Test
    fun `a refund never takes the budget below zero or creates a bucket`() {
        val quota = RedisLinkPreviewQuotaAdapter(template, LinkPreviewProperties(fetchesPerUserPerMinute = 1))
        val user = UUID.randomUUID()
        avoidMinuteBoundary()

        repeat(3) { quota.refund(user) }
        assertTrue(template.keys("linkpreview:quota:$user:*").isEmpty(), "a refund must not create a key")

        assertTrue(quota.tryConsume(user))
        assertFalse(quota.tryConsume(user), "earlier refunds must not have banked extra budget")
    }

    @Test
    fun `a busy fetcher leaves nothing in the real cache and charges nothing`() {
        val url = uniqueUrl()
        val user = UUID.randomUUID()
        val properties = LinkPreviewProperties(fetchesPerUserPerMinute = 1)
        val cache = RedisLinkPreviewCacheAdapter(template)
        val quota = RedisLinkPreviewQuotaAdapter(template, properties)
        val busy = object : LinkPreviewFetchPort {
            override fun fetch(url: String): LinkPreviewFetchResult = LinkPreviewFetchResult.Busy
        }
        val service = LinkPreviewService(busy, cache, quota, properties.cacheTtl, properties.failureCacheTtl)
        avoidMinuteBoundary()

        assertTrue(service.getLinkPreview(user, url).isEmpty)

        assertNull(cache.get(url), "busy must not be cached as the URL's preview")
        assertTrue(quota.tryConsume(user), "the unit charged for the busy attempt was refunded")
    }

    /** Fixed one-minute buckets: a test that straddles a boundary would see a legitimate reset. */
    private fun avoidMinuteBoundary() {
        val secondOfMinute = (System.currentTimeMillis() / 1000) % 60
        if (secondOfMinute >= 58) Thread.sleep(3_000)
    }
}
