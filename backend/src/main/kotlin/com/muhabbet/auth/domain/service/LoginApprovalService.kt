package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.LoginApproval
import com.muhabbet.auth.domain.model.LoginApprovalStatus
import com.muhabbet.auth.domain.port.`in`.LoginApprovalUseCase
import com.muhabbet.auth.domain.port.out.LoginApprovalRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class LoginApprovalService(
    private val loginApprovalRepository: LoginApprovalRepository
) : LoginApprovalUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val APPROVAL_EXPIRY_SECONDS = 300L // 5 minutes
    }

    @Transactional
    override fun requestApproval(userId: UUID, deviceName: String?, platform: String?, ip: String?): LoginApproval {
        val approval = LoginApproval(
            userId = userId,
            deviceName = deviceName,
            platform = platform,
            ipAddress = ip,
            expiresAt = Instant.now().plusSeconds(APPROVAL_EXPIRY_SECONDS)
        )
        val saved = loginApprovalRepository.save(approval)
        log.info("Login approval requested: id={}, user={}, device={}", saved.id, userId, deviceName)
        return saved
    }

    @Transactional
    override fun approveLogin(approvalId: UUID, userId: UUID): LoginApproval {
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
        return saved
    }

    @Transactional
    override fun denyLogin(approvalId: UUID, userId: UUID): LoginApproval {
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
        return saved
    }

    @Transactional(readOnly = true)
    override fun getPendingApprovals(userId: UUID): List<LoginApproval> =
        loginApprovalRepository.findPendingByUserId(userId)
            .filter { it.expiresAt.isAfter(Instant.now()) }
}
