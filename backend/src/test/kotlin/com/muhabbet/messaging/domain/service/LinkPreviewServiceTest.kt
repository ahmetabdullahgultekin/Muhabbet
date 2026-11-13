package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.LinkPreview
import com.muhabbet.messaging.domain.port.out.LinkPreviewCachePort
import com.muhabbet.messaging.domain.port.out.LinkPreviewFetchPort
import com.muhabbet.messaging.domain.port.out.LinkPreviewQuotaPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

/**
 * Cache first, then budget, then fetch (#916). The cache here is an in-memory fake rather than a
 * mock so "the second identical request makes no outbound call" is tested as behaviour, not as an
 * interaction script.
 */
class LinkPreviewServiceTest {

    private class InMemoryCache : LinkPreviewCachePort {
        val entries = mutableMapOf<String, Pair<LinkPreview, Duration>>()
        override fun get(url: String) = entries[url]?.first
        override fun put(preview: LinkPreview, ttl: Duration) {
            entries[preview.url] = preview to ttl
        }
    }

    private val fetcher = mockk<LinkPreviewFetchPort>()
    private val quota = mockk<LinkPreviewQuotaPort>()
    private val cache = InMemoryCache()
    private val successTtl = Duration.ofHours(6)
    private val failureTtl = Duration.ofMinutes(10)
    private val service = LinkPreviewService(fetcher, cache, quota, successTtl, failureTtl)
    private val user = UUID.randomUUID()
    private val url = "https://example.com/a"

    @Test
    fun `should serve a second identical request from cache without fetching or charging the budget`() {
        every { quota.tryConsume(user) } returns true
        every { fetcher.fetch(url) } returns LinkPreview(url, title = "A")

        val first = service.getLinkPreview(user, url)
        val second = service.getLinkPreview(user, url)

        assertEquals(first, second)
        verify(exactly = 1) { fetcher.fetch(url) }
        verify(exactly = 1) { quota.tryConsume(user) }
    }

    @Test
    fun `should share the cache across users`() {
        val other = UUID.randomUUID()
        every { quota.tryConsume(any()) } returns true
        every { fetcher.fetch(url) } returns LinkPreview(url, title = "A")

        service.getLinkPreview(user, url)
        service.getLinkPreview(other, url)

        verify(exactly = 1) { fetcher.fetch(url) }
        verify(exactly = 0) { quota.tryConsume(other) }
    }

    @Test
    fun `should cache a successful preview for the success ttl`() {
        every { quota.tryConsume(user) } returns true
        every { fetcher.fetch(url) } returns LinkPreview(url, title = "A")

        service.getLinkPreview(user, url)

        assertEquals(successTtl, cache.entries.getValue(url).second)
    }

    @Test
    fun `should cache a failed fetch as an empty preview for the shorter failure ttl`() {
        every { quota.tryConsume(user) } returns true
        every { fetcher.fetch(url) } returns null

        val preview = service.getLinkPreview(user, url)

        assertTrue(preview.isEmpty)
        assertEquals(url, preview.url)
        assertEquals(failureTtl, cache.entries.getValue(url).second)
        service.getLinkPreview(user, url)
        verify(exactly = 1) { fetcher.fetch(url) }
    }

    @Test
    fun `should refuse with RATE_LIMITED and not fetch when the budget is spent`() {
        every { quota.tryConsume(user) } returns false

        val error = assertThrows(BusinessException::class.java) { service.getLinkPreview(user, url) }

        assertEquals(ErrorCode.RATE_LIMITED, error.errorCode)
        verify(exactly = 0) { fetcher.fetch(any()) }
        assertTrue(cache.entries.isEmpty(), "a refusal must not be cached as the page's preview")
    }

    @Test
    fun `should answer a cache hit even when the budget is spent`() {
        cache.put(LinkPreview(url, title = "cached"), successTtl)
        every { quota.tryConsume(user) } returns false

        assertEquals("cached", service.getLinkPreview(user, url).title)
    }

    @Test
    fun `should reject a blank or oversized url as a validation error before anything else`() {
        listOf("   ", "https://x.test/" + "a".repeat(LinkPreviewService.MAX_URL_LENGTH)).forEach { bad ->
            val error = assertThrows(BusinessException::class.java) { service.getLinkPreview(user, bad) }
            assertEquals(ErrorCode.VALIDATION_ERROR, error.errorCode)
        }
        verify(exactly = 0) { quota.tryConsume(any()) }
    }

    @Test
    fun `should key the cache by the requested url, not wherever the page redirected to`() {
        every { quota.tryConsume(user) } returns true
        every { fetcher.fetch(url) } returns LinkPreview("https://elsewhere.test/", title = "A")

        assertEquals(url, service.getLinkPreview(user, url).url)
        assertTrue(url in cache.entries)
    }
}
