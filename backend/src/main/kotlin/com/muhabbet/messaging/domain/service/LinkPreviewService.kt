package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.LinkPreview
import com.muhabbet.messaging.domain.model.LinkPreviewFetchResult
import com.muhabbet.messaging.domain.port.`in`.GetLinkPreviewUseCase
import com.muhabbet.messaging.domain.port.out.LinkPreviewCachePort
import com.muhabbet.messaging.domain.port.out.LinkPreviewFetchPort
import com.muhabbet.messaging.domain.port.out.LinkPreviewQuotaPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import java.time.Duration
import java.util.UUID

/**
 * Cache first, then budget, then fetch (#916).
 *
 * The order is the point. A cache hit makes no outbound request, so it is free and does not touch
 * the budget — scrolling back through a chat full of links must not rate-limit anyone. Only a miss,
 * which is the thing that costs the server a fetch on the caller's behalf, is charged.
 *
 * Failures are cached too, for less time: a URL that is blocked, unreachable or not HTML would
 * otherwise be re-fetched on every render of every bubble that contains it.
 *
 * **Busy is not a failure and is never cached.** When the fetcher has no free slot, nothing was
 * learned about the URL; caching that as the URL's answer would let a few slow servers held open by
 * a few accounts blank out every other user's previews for the failure TTL, through the shared
 * cache. The caller gets an empty preview for this request only, and the budget unit it was charged
 * is refunded, since no fetch was made on its behalf.
 */
class LinkPreviewService(
    private val fetcher: LinkPreviewFetchPort,
    private val cache: LinkPreviewCachePort,
    private val quota: LinkPreviewQuotaPort,
    private val successTtl: Duration,
    private val failureTtl: Duration,
) : GetLinkPreviewUseCase {

    override fun getLinkPreview(requesterId: UUID, url: String): LinkPreview {
        val requested = url.trim()
        if (requested.isEmpty() || requested.length > MAX_URL_LENGTH) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }

        cache.get(requested)?.let { return it }

        if (!quota.tryConsume(requesterId)) throw BusinessException(ErrorCode.RATE_LIMITED)

        return when (val result = fetcher.fetch(requested)) {
            is LinkPreviewFetchResult.Fetched -> remember(result.preview.copy(url = requested))
            LinkPreviewFetchResult.Unavailable -> remember(LinkPreview(url = requested))
            LinkPreviewFetchResult.Busy -> {
                quota.refund(requesterId)
                LinkPreview(url = requested)
            }
        }
    }

    /** A page with no metadata is as much an answer about the URL as a refusal is: short TTL. */
    private fun remember(preview: LinkPreview): LinkPreview {
        cache.put(preview, if (preview.isEmpty) failureTtl else successTtl)
        return preview
    }

    companion object {
        /** Longer than any URL a person pastes; short enough that a cache key cannot be abused. */
        const val MAX_URL_LENGTH = 2048
    }
}
