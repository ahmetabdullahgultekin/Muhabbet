package com.muhabbet.media.domain.model

import java.time.Instant
import java.util.UUID

data class MediaFile(
    val id: UUID = UUID.randomUUID(),
    val uploaderId: UUID,
    val fileKey: String,
    val contentType: String,
    val sizeBytes: Long,
    val thumbnailKey: String? = null,
    val originalFilename: String? = null,
    val durationSeconds: Int? = null,
    val createdAt: Instant = Instant.now()
)
