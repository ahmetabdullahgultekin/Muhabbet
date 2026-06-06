package com.muhabbet.media.adapter.out.persistence.repository

import com.muhabbet.media.adapter.out.persistence.entity.MediaFileJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * Membership lookup for media authorization. Uses a NATIVE query against the shared schema
 * (messages + conversation_members) so the media module does not need to import messaging JPA
 * entities — keeping the modules decoupled at the type level (ArchUnit-safe).
 *
 * A user is authorized for a media blob if they are a member of any conversation that has a
 * non-deleted message whose media_url references the media's file_key.
 */
interface MediaAccessQueryRepository : JpaRepository<MediaFileJpaEntity, UUID> {

    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1
            FROM messages m
            JOIN conversation_members cm ON cm.conversation_id = m.conversation_id
            WHERE cm.user_id = :userId
              AND m.is_deleted = false
              AND m.media_url LIKE CONCAT('%', :fileKey, '%')
        )
        """,
        nativeQuery = true
    )
    fun isMemberOfConversationContainingMedia(
        @Param("userId") userId: UUID,
        @Param("fileKey") fileKey: String
    ): Boolean
}
