package com.muhabbet.media.domain.port.out

import java.util.UUID

/**
 * Authorization gate for media access (Phase 0 / P0-5/P0-8/P0-13).
 *
 * A user may obtain a presigned URL for a media file only if they are a member of a conversation
 * that contains a message referencing that media. (Uploader ownership is checked separately in the
 * service from the MediaFile.uploaderId.) Kept as an out-port so the media domain stays free of any
 * direct dependency on the messaging module's services — the adapter resolves membership via the
 * shared persistence layer.
 */
interface MediaAccessPolicy {
    /**
     * @return true if [userId] is a member of at least one conversation whose messages reference
     *         the media identified by [fileKey].
     */
    fun isMemberOfConversationContainingMedia(userId: UUID, fileKey: String): Boolean
}
