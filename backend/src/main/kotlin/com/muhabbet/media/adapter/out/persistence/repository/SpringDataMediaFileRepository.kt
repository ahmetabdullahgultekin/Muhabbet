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
}
