package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.LinkPreview
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

        val preview = fetcher.fetch(requested)?.copy(url = requested) ?: LinkPreview(url = requested)
        cache.put(preview, if (preview.isEmpty) failureTtl else successTtl)
        return preview
    }

    companion object {
        /** Longer than any URL a person pastes; short enough that a cache key cannot be abused. */
        const val MAX_URL_LENGTH = 2048
    }
}
