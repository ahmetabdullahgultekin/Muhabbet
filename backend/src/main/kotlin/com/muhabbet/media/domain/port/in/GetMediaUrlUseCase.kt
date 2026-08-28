package com.muhabbet.media.domain.port.`in`

import java.util.UUID

interface GetMediaUrlUseCase {
    /**
     * Issues a presigned URL for [mediaId] only if [requestingUserId] uploaded it. Throws
     * MEDIA_FORBIDDEN (403) otherwise, MEDIA_NOT_FOUND (404) when there is no such object.
     *
     * The "or a member of a conversation referencing it" half this used to promise was removed by
     * #267 and the comment kept promising it for months: membership was decided from
     * `messages.media_url`, a client-written string, so anyone could name someone else's file. It
     * comes back when the question can be asked of a server-resolved id (#719), not before.
     */
    fun getPresignedUrl(mediaId: UUID, requestingUserId: UUID): MediaUrlResult
}

data class MediaUrlResult(
    val url: String,
    val thumbnailUrl: String?
)
