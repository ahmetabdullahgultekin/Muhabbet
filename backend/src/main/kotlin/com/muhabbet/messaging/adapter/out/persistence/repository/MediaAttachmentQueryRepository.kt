package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Send-time media checks. Uses NATIVE queries against `media_files` so the messaging module does not
 * import media JPA entities — the same decoupling `MediaAccessQueryRepository` uses in the other
 * direction to reach `messages`.
 */
interface MediaAttachmentQueryRepository : JpaRepository<MessageJpaEntity, UUID> {

    /**
     * True when some media file uploaded by this user has its key inside [mediaUrl].
     *
     * Matches on containment rather than equality because `media_url` carries whatever form the client
     * built — bare key or full URL — and the download-side check in `MediaAccessQueryRepository` reads
     * it the same way. The two have to agree, or a message would pass one and fail the other.
     */
    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1
            FROM media_files mf
            WHERE mf.uploader_id = :userId
              AND :mediaUrl LIKE CONCAT('%', mf.file_key, '%')
        )
        """,
        nativeQuery = true
    )
    fun ownsMedia(
        @Param("userId") userId: UUID,
        @Param("mediaUrl") mediaUrl: String
    ): Boolean

    /**
     * True when this user can already download the media through an existing message, which is what
     * makes forwarding legal: they are passing on something they were already shown.
     *
     * Deliberately looks only at messages that already exist. The message being sent has not been
     * written yet, so it cannot vouch for itself — that self-reference was the flaw.
     */
    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1
            FROM messages m
            JOIN conversation_members cm ON cm.conversation_id = m.conversation_id
            WHERE cm.user_id = :userId
              AND m.is_deleted = false
              AND m.media_url = :mediaUrl
        )
        """,
        nativeQuery = true
    )
    fun canAlreadyReachMedia(
        @Param("userId") userId: UUID,
        @Param("mediaUrl") mediaUrl: String
    ): Boolean
}
