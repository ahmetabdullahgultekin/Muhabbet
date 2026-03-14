package com.muhabbet.auth.domain.port.`in`

import com.muhabbet.auth.domain.model.LoginApproval
import java.util.UUID

interface LoginApprovalUseCase {
    fun requestApproval(userId: UUID, deviceName: String?, platform: String?, ip: String?): LoginApproval
    fun approveLogin(approvalId: UUID, userId: UUID): LoginApproval
    fun denyLogin(approvalId: UUID, userId: UUID): LoginApproval
    fun getPendingApprovals(userId: UUID): List<LoginApproval>
}
