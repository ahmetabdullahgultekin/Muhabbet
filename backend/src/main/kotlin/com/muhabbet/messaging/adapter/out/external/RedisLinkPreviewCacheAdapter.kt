package com.muhabbet.messaging.adapter.out.external

import com.muhabbet.messaging.domain.model.LinkPreview
import com.muhabbet.messaging.domain.port.out.LinkPreviewCachePort
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration

/**
 * Link previews in Redis, keyed by a SHA-256 of the requested URL (#916).
 *
 * Hashed rather than stored raw so the key space is fixed-length and a key listing does not read as
 * a log of every link anyone has sent — the value still holds the URL, because the preview is about
 * it, but `KEYS linkpreview:*` does not.
 *
 * **Fails open on a Redis outage**: a read error is a miss and a write error is dropped. The cost of
 * that is a fetch that would otherwise have been cached, still behind the per-user budget and the
 * fetcher's bulkhead; the alternative is link previews breaking whenever Redis does.
 */
@Component
class RedisLinkPreviewCacheAdapter(
    private val redisTemplate: StringRedisTemplate,
) : LinkPreviewCachePort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun get(url: String): LinkPreview? = try {
        redisTemplate.opsForValue().get(key(url))?.let { JSON.decodeFromString<CachedPreview>(it).toDomain() }
    } catch (e: Exception) {
        log.warn("Link preview cache read failed; treating as a miss: {}", e.toString())
        null
    }

    override fun put(preview: LinkPreview, ttl: Duration) {
        try {
            redisTemplate.opsForValue().set(key(preview.url), JSON.encodeToString(CachedPreview.of(preview)), ttl)
        } catch (e: Exception) {
            log.warn("Link preview cache write failed; not cached: {}", e.toString())
        }
    }

    private fun key(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
        return KEY_PREFIX + digest.joinToString("") { "%02x".format(it) }
    }

    /** The stored shape, versioned through [KEY_PREFIX] so a change to it cannot misread old rows. */
    @Serializable
    private data class CachedPreview(
        val url: String,
        val title: String? = null,
        val description: String? = null,
        val imageUrl: String? = null,
        val siteName: String? = null,
    ) {
        fun toDomain() = LinkPreview(url, title, description, imageUrl, siteName)

        companion object {
            fun of(p: LinkPreview) = CachedPreview(p.url, p.title, p.description, p.imageUrl, p.siteName)
        }
    }

    private companion object {
        const val KEY_PREFIX = "linkpreview:v1:"
        val JSON = Json { ignoreUnknownKeys = true }
    }
}
