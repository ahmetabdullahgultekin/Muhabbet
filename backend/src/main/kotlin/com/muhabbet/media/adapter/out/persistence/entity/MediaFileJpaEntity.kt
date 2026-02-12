package com.muhabbet.media.adapter.out.persistence.entity

import com.muhabbet.media.domain.model.MediaFile
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "media_files")
class MediaFileJpaEntity(
    @Id
    val id: UUID,

    @Column(name = "uploader_id", nullable = false)
    val uploaderId: UUID,

    @Column(name = "file_key", nullable = false)
    val fileKey: String,

    @Column(name = "content_type", nullable = false)
    val contentType: String,

    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,

    @Column(name = "thumbnail_key")
    val thumbnailKey: String? = null,

    @Column(name = "original_filename")
    val originalFilename: String? = null,

    @Column(name = "duration_seconds")
    val durationSeconds: Int? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): MediaFile = MediaFile(
        id = id,
        uploaderId = uploaderId,
        fileKey = fileKey,
        contentType = contentType,
        sizeBytes = sizeBytes,
        thumbnailKey = thumbnailKey,
        originalFilename = originalFilename,
        durationSeconds = durationSeconds,
        createdAt = createdAt
    )

    companion object {
        fun fromDomain(m: MediaFile): MediaFileJpaEntity = MediaFileJpaEntity(
            id = m.id,
            uploaderId = m.uploaderId,
            fileKey = m.fileKey,
            contentType = m.contentType,
            sizeBytes = m.sizeBytes,
            thumbnailKey = m.thumbnailKey,
            originalFilename = m.originalFilename,
            durationSeconds = m.durationSeconds,
            createdAt = m.createdAt
        )
    }
}
