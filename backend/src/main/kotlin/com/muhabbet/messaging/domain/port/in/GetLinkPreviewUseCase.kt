package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.LinkPreview
import java.util.UUID

interface GetLinkPreviewUseCase {

    /**
     * The preview for [url], from cache when one exists. A page that cannot or may not be fetched
     * yields an empty [LinkPreview], not an error — the client shows the bare link either way.
     *
     * Throws `BusinessException(RATE_LIMITED)` when [requesterId] has spent their outbound-fetch
     * budget. Cache hits cost nothing against that budget.
     */
    fun getLinkPreview(requesterId: UUID, url: String): LinkPreview
}
