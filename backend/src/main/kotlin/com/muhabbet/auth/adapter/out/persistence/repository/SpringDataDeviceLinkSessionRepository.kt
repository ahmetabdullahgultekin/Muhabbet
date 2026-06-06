package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.DeviceLinkSessionJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataDeviceLinkSessionRepository : JpaRepository<DeviceLinkSessionJpaEntity, UUID> {
    fun findByLinkToken(linkToken: String): DeviceLinkSessionJpaEntity?
}
