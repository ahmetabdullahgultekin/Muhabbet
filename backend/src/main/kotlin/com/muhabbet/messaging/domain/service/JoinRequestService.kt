package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.ManageJoinRequestUseCase
import com.muhabbet.messaging.domain.port.`in`.PendingJoinRequest
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.GroupJoinRequestRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class JoinRequestService(
    private val joinRequestRepository: GroupJoinRequestRepository,
    private val conversationRepository: ConversationRepository,
    private val inviteLinkRepository: GroupInviteLinkRepository,
    private val userDirectory: UserDirectoryPort
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

    /**
     * Admits the requester, at most once (#788).
     *
     * Three guards were missing before the app had a caller for this: the request was never checked
     * against the group in the URL, its status was never checked — so a rejected request could be
     * "approved" later, and an approved one approved again into a duplicate membership row — and a
     * requester who had meanwhile been added by hand was added a second time. A request in another
     * group or no longer pending is reported as not found, which is also what it is from this
     * group's point of view.
     */
    @Transactional
    override fun approveJoin(conversationId: UUID, requestId: UUID, adminId: UUID) {
        val request = requirePending(conversationId, requestId)
        requireAdminOrOwner(request.conversationId, adminId)

        joinRequestRepository.save(
            request.copy(status = JoinRequestStatus.APPROVED, reviewedBy = adminId, reviewedAt = Instant.now())
        )

        if (conversationRepository.findMember(request.conversationId, request.userId) == null) {
            conversationRepository.saveMember(
                ConversationMember(
                    conversationId = request.conversationId,
                    userId = request.userId,
                    role = MemberRole.MEMBER
                )
            )
        }

        // Increment invite link use count if applicable
        if (request.inviteLinkId != null) {
            inviteLinkRepository.incrementUseCount(request.inviteLinkId)
        }

        log.info("Join request approved: id={}, user={}, by={}", requestId, request.userId, adminId)
    }

    @Transactional
    override fun rejectJoin(conversationId: UUID, requestId: UUID, adminId: UUID) {
        val request = requirePending(conversationId, requestId)
        requireAdminOrOwner(request.conversationId, adminId)

        joinRequestRepository.save(
            request.copy(status = JoinRequestStatus.REJECTED, reviewedBy = adminId, reviewedAt = Instant.now())
        )
        log.info("Join request rejected: id={}, user={}, by={}", requestId, request.userId, adminId)
    }

    /**
     * Admins and the owner only (#788). This was open to any authenticated caller for any group id:
     * the list says who is asking to join a group, which is not something a non-member — or an
     * ordinary member — has any business enumerating.
     */
    @Transactional(readOnly = true)
    override fun getPendingRequests(conversationId: UUID, requesterId: UUID): List<PendingJoinRequest> {
        requireAdminOrOwner(conversationId, requesterId)
        val pending = joinRequestRepository.findPendingByConversationId(conversationId)
        if (pending.isEmpty()) return emptyList()
        val directory = userDirectory.findDisplayInfo(pending.map { it.userId }.toSet())
        return pending.map { request ->
            val info = directory[request.userId]
            PendingJoinRequest(request = request, displayName = info?.displayName, avatarUrl = info?.avatarUrl)
        }
    }

    private fun requirePending(conversationId: UUID, requestId: UUID): GroupJoinRequest {
        val request = joinRequestRepository.findById(requestId)
        if (request == null || request.conversationId != conversationId || request.status != JoinRequestStatus.PENDING) {
            throw BusinessException(ErrorCode.JOIN_REQUEST_NOT_FOUND)
        }
        return request
    }

    private fun requireAdminOrOwner(conversationId: UUID, userId: UUID) {
        val member = conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)
        if (member.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
    }
}
