package com.muhabbet.media.adapter.out.persistence.repository

import com.muhabbet.media.adapter.out.persistence.entity.MediaFileJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataMediaFileRepository : JpaRepository<MediaFileJpaEntity, UUID>
