package com.muhabbet.media.adapter.out.persistence.repository

import com.muhabbet.media.adapter.out.persistence.entity.MediaFileJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface SpringDataMediaFileRepository : JpaRepository<MediaFileJpaEntity, UUID> {

    @Query("SELECT COALESCE(SUM(m.sizeBytes), 0) FROM MediaFileJpaEntity m WHERE m.uploaderId = :uploaderId AND m.contentType LIKE :prefix%")
    fun sumSizeByUploaderAndContentTypePrefix(uploaderId: UUID, prefix: String): Long

    @Query("SELECT COUNT(m) FROM MediaFileJpaEntity m WHERE m.uploaderId = :uploaderId AND m.contentType LIKE :prefix%")
    fun countByUploaderAndContentTypePrefix(uploaderId: UUID, prefix: String): Long

    /**
     * Equality on both key columns, never `LIKE '%' || :key || '%'`. A substring comparison would
     * match any key that merely *contains* the one asked about, which is a comparison the caller
     * does not control the left-hand side of — the whole reason the download-side authorization
     * query built that way had to be deleted (#267).
     *
     * Returns a list so a duplicated key is a first-row-wins answer rather than a 500 from
     * `NonUniqueResultException`; keys carry a random UUID, so this should never have two rows.
     */
    @Query("SELECT m FROM MediaFileJpaEntity m WHERE m.fileKey = :key OR m.thumbnailKey = :key")
    fun findByObjectKey(key: String): List<MediaFileJpaEntity>
}
