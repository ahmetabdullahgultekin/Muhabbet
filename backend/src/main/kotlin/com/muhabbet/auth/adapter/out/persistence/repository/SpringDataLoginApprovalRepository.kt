package com.muhabbet.auth.adapter.out.persistence.repository

import com.muhabbet.auth.adapter.out.persistence.entity.LoginApprovalJpaEntity
import com.muhabbet.auth.domain.model.LoginApprovalStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataLoginApprovalRepository : JpaRepository<LoginApprovalJpaEntity, UUID> {
    fun findByUserIdAndStatus(userId: UUID, status: LoginApprovalStatus): List<LoginApprovalJpaEntity>
}
