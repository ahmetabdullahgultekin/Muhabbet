package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.LoginApproval
import com.muhabbet.auth.domain.model.LoginApprovalStatus
import com.muhabbet.auth.domain.port.`in`.LoginApprovalUseCase
import com.muhabbet.auth.domain.port.out.LoginApprovalRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class LoginApprovalService(
    private val loginApprovalRepository: LoginApprovalRepository,
    private val transactions: TransactionRunner
) : LoginApprovalUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val APPROVAL_EXPIRY_SECONDS = 300L // 5 minutes
    }

    override fun requestApproval(userId: UUID, deviceName: String?, platform: String?, ip: String?): LoginApproval = transactions.inTransaction {
        val approval = LoginApproval(
            userId = userId,
            deviceName = deviceName,
            platform = platform,
            ipAddress = ip,
            expiresAt = Instant.now().plusSeconds(APPROVAL_EXPIRY_SECONDS)
        )
        val saved = loginApprovalRepository.save(approval)
        log.info("Login approval requested: id={}, user={}, device={}", saved.id, userId, deviceName)
        return@inTransaction saved
    }

    override fun approveLogin(approvalId: UUID, userId: UUID): LoginApproval = transactions.inTransaction {
        val approval = loginApprovalRepository.findById(approvalId)
            ?: throw BusinessException(ErrorCode.LOGIN_APPROVAL_NOT_FOUND)

        if (approval.userId != userId) {
            throw BusinessException(ErrorCode.LOGIN_APPROVAL_NOT_FOUND)
        }

        if (approval.expiresAt.isBefore(Instant.now())) {
            throw BusinessException(ErrorCode.LOGIN_APPROVAL_EXPIRED)
        }

        val updated = approval.copy(
            status = LoginApprovalStatus.APPROVED,
            resolvedAt = Instant.now()
        )
        val saved = loginApprovalRepository.save(updated)
        log.info("Login approved: id={}, user={}", approvalId, userId)
        return@inTransaction saved
    }

    override fun denyLogin(approvalId: UUID, userId: UUID): LoginApproval = transactions.inTransaction {
        val approval = loginApprovalRepository.findById(approvalId)
            ?: throw BusinessException(ErrorCode.LOGIN_APPROVAL_NOT_FOUND)

        if (approval.userId != userId) {
            throw BusinessException(ErrorCode.LOGIN_APPROVAL_NOT_FOUND)
        }

        val updated = approval.copy(
            status = LoginApprovalStatus.DENIED,
            resolvedAt = Instant.now()
        )
        val saved = loginApprovalRepository.save(updated)
        log.info("Login denied: id={}, user={}", approvalId, userId)
        return@inTransaction saved
    }

    override fun getPendingApprovals(userId: UUID): List<LoginApproval> = transactions.inReadOnlyTransaction {
        loginApprovalRepository.findPendingByUserId(userId)
        .filter { it.expiresAt.isAfter(Instant.now()) }
    }
}
