package com.muhabbet.auth.adapter.out.persistence

import com.muhabbet.auth.adapter.out.persistence.entity.LoginApprovalJpaEntity
import com.muhabbet.auth.adapter.out.persistence.repository.SpringDataLoginApprovalRepository
import com.muhabbet.auth.domain.model.LoginApproval
import com.muhabbet.auth.domain.model.LoginApprovalStatus
import com.muhabbet.auth.domain.port.out.LoginApprovalRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class LoginApprovalPersistenceAdapter(
    private val repo: SpringDataLoginApprovalRepository
) : LoginApprovalRepository {

    override fun save(approval: LoginApproval): LoginApproval =
        repo.save(LoginApprovalJpaEntity.fromDomain(approval)).toDomain()

    override fun findById(id: UUID): LoginApproval? =
        repo.findById(id).orElse(null)?.toDomain()

    override fun findPendingByUserId(userId: UUID): List<LoginApproval> =
        repo.findByUserIdAndStatus(userId, LoginApprovalStatus.PENDING).map { it.toDomain() }
}
