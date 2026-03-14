package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.ManageJoinRequestUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.GroupJoinRequestRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class JoinRequestService(
    private val joinRequestRepository: GroupJoinRequestRepository,
    private val conversationRepository: ConversationRepository,
    private val inviteLinkRepository: GroupInviteLinkRepository
) : ManageJoinRequestUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun requestJoin(conversationId: UUID, userId: UUID, linkId: UUID?): GroupJoinRequest {
        // Check not already a member
        val existing = conversationRepository.findMember(conversationId, userId)
        if (existing != null) {
            throw BusinessException(ErrorCode.GROUP_ALREADY_MEMBER)
        }

        // Check no existing pending request
        val pendingRequest = joinRequestRepository.findByConversationIdAndUserIdAndStatus(
            conversationId, userId, JoinRequestStatus.PENDING
        )
        if (pendingRequest != null) {
            throw BusinessException(ErrorCode.JOIN_REQUEST_ALREADY_EXISTS)
        }

        val request = GroupJoinRequest(
            conversationId = conversationId,
            userId = userId,
            inviteLinkId = linkId
        )
        val saved = joinRequestRepository.save(request)
        log.info("Join request created: conv={}, user={}", conversationId, userId)
        return saved
    }

    @Transactional
    override fun approveJoin(requestId: UUID, adminId: UUID) {
        val request = joinRequestRepository.findById(requestId)
            ?: throw BusinessException(ErrorCode.JOIN_REQUEST_NOT_FOUND)

        requireAdminOrOwner(request.conversationId, adminId)

        val updated = request.copy(
            status = JoinRequestStatus.APPROVED,
            reviewedBy = adminId,
            reviewedAt = Instant.now()
        )
        joinRequestRepository.save(updated)

        // Add user as member
        conversationRepository.saveMember(
            ConversationMember(
                conversationId = request.conversationId,
                userId = request.userId,
                role = MemberRole.MEMBER
            )
        )

        // Increment invite link use count if applicable
        if (request.inviteLinkId != null) {
            inviteLinkRepository.incrementUseCount(request.inviteLinkId)
        }

        log.info("Join request approved: id={}, user={}, by={}", requestId, request.userId, adminId)
    }

    @Transactional
    override fun rejectJoin(requestId: UUID, adminId: UUID) {
        val request = joinRequestRepository.findById(requestId)
            ?: throw BusinessException(ErrorCode.JOIN_REQUEST_NOT_FOUND)

        requireAdminOrOwner(request.conversationId, adminId)

        val updated = request.copy(
            status = JoinRequestStatus.REJECTED,
            reviewedBy = adminId,
            reviewedAt = Instant.now()
        )
        joinRequestRepository.save(updated)
        log.info("Join request rejected: id={}, user={}, by={}", requestId, request.userId, adminId)
    }

    @Transactional(readOnly = true)
    override fun getPendingRequests(conversationId: UUID): List<GroupJoinRequest> =
        joinRequestRepository.findPendingByConversationId(conversationId)

    private fun requireAdminOrOwner(conversationId: UUID, userId: UUID) {
        val member = conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)
        if (member.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
    }
}
