package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.LoginApproval
import com.muhabbet.auth.domain.model.LoginApprovalStatus
import java.util.UUID

interface LoginApprovalRepository {
    fun save(approval: LoginApproval): LoginApproval
    fun findById(id: UUID): LoginApproval?
    fun findPendingByUserId(userId: UUID): List<LoginApproval>
}
