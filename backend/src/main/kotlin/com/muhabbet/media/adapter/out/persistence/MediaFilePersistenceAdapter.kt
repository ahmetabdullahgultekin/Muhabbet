package com.muhabbet.media.adapter.out.persistence

import com.muhabbet.media.adapter.out.persistence.entity.MediaFileJpaEntity
import com.muhabbet.media.adapter.out.persistence.repository.SpringDataMediaFileRepository
import com.muhabbet.media.domain.model.MediaFile
import com.muhabbet.media.domain.port.out.MediaFileRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class MediaFilePersistenceAdapter(
    private val springDataMediaFileRepository: SpringDataMediaFileRepository
) : MediaFileRepository {

    override fun save(mediaFile: MediaFile): MediaFile =
        springDataMediaFileRepository.save(MediaFileJpaEntity.fromDomain(mediaFile)).toDomain()

    override fun findById(id: UUID): MediaFile? =
        springDataMediaFileRepository.findById(id).orElse(null)?.toDomain()

    override fun sumSizeByUploaderAndContentTypePrefix(uploaderId: UUID, prefix: String): Long =
        springDataMediaFileRepository.sumSizeByUploaderAndContentTypePrefix(uploaderId, prefix)

    override fun countByUploaderAndContentTypePrefix(uploaderId: UUID, prefix: String): Long =
        springDataMediaFileRepository.countByUploaderAndContentTypePrefix(uploaderId, prefix)
}
