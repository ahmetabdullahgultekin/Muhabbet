package com.muhabbet.media.domain.port.`in`

import java.util.UUID

interface GetMediaUrlUseCase {
    fun getPresignedUrl(mediaId: UUID): MediaUrlResult
}

data class MediaUrlResult(
    val url: String,
    val thumbnailUrl: String?
)
