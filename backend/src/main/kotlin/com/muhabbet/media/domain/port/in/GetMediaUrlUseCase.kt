package com.muhabbet.media.domain.port.`in`

import java.util.UUID

interface GetMediaUrlUseCase {
    /**
     * Issues a presigned URL for [mediaId] only if [requestingUserId] is the uploader OR a member of
     * a conversation that references the media. Throws MEDIA_FORBIDDEN (403) otherwise.
     */
    fun getPresignedUrl(mediaId: UUID, requestingUserId: UUID): MediaUrlResult
}

data class MediaUrlResult(
    val url: String,
    val thumbnailUrl: String?
)
