package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestReview
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.ManageJoinRequestUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.GroupJoinRequestRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

/**
 * The join-request lifecycle: PENDING -> APPROVED | REJECTED, once (#888).
 *
 * Three things used to be missing. Approve and reject never looked at the status, so a rejected
 * request could be approved later and a removed user re-added by a stale click. Filing a request
 * needed no link at all, so anyone could queue themselves for any conversation id. And the pending
 * list had no access check, so anyone could read who was waiting to join any group. The transition
 * itself is pure ([GroupJoinRequest.approve] / [GroupJoinRequest.reject]); the write is conditional
 * on PENDING ([GroupJoinRequestRepository.markReviewed]) so two admins racing on one request
 * cannot both win; and admission goes through [GroupAdmission] so the cap holds and the group is told.
 */
open class JoinRequestService(
    private val joinRequestRepository: GroupJoinRequestRepository,
    private val conversationRepository: ConversationRepository,
    private val inviteLinkRepository: GroupInviteLinkRepository,
    private val admission: GroupAdmission,
    private val transactions: TransactionRunner,
    private val clock: Clock = Clock.systemUTC()
) : ManageJoinRequestUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * A request is filed against an approval-required invite link of *this* group. A missing,
     * foreign, revoked or expired link all answer INVITE_LINK_NOT_FOUND, so the endpoint says nothing
     * about conversations the caller holds no link to.
     */
    @Transactional
    override fun requestJoin(conversationId: UUID, userId: UUID, linkId: UUID?): GroupJoinRequest {
        val link = linkId?.let { inviteLinkRepository.findById(it) }
            ?.takeIf { it.conversationId == conversationId && it.isActive }
            ?.takeIf { it.expiresAt == null || it.expiresAt.isAfter(clock.instant()) }
            ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)

        // A link that admits directly has no review step; queueing a request on it would leave the
        // request waiting for an approval the link never asked for.
        if (!link.requiresApproval) throw BusinessException(ErrorCode.VALIDATION_ERROR)

        if (conversationRepository.findMember(conversationId, userId) != null) {
            throw BusinessException(ErrorCode.GROUP_ALREADY_MEMBER)
        }
        val pending = joinRequestRepository.findByConversationIdAndUserIdAndStatus(
            conversationId, userId, JoinRequestStatus.PENDING
        )
        if (pending != null) throw BusinessException(ErrorCode.JOIN_REQUEST_ALREADY_EXISTS)

        val saved = joinRequestRepository.save(
            GroupJoinRequest(conversationId = conversationId, userId = userId, inviteLinkId = link.id)
        )
        log.info("Join request created: conv={}, user={}", conversationId, userId)
        return saved
    }

    override fun approveJoin(requestId: UUID, adminId: UUID) {
        val admitted = transactions.inTransaction { persistApproval(requestId, adminId) }
        admitted.value?.let { admission.announce(it, addedBy = adminId) }
        log.info("Join request approved: id={}, by={}", requestId, adminId)
    }

    /** Non-null wrapper: [TransactionRunner.inTransaction] cannot return null. */
    private data class ApprovalOutcome(val value: GroupAdmission.Admitted?)

    private fun persistApproval(requestId: UUID, adminId: UUID): ApprovalOutcome {
        val request = joinRequestRepository.findById(requestId)
            ?: throw BusinessException(ErrorCode.JOIN_REQUEST_NOT_FOUND)
        requireAdminOrOwner(request.conversationId, adminId)

        commitReview(request.approve(adminId, clock.instant()))

        // Null when they joined another way meanwhile: the request is still answered, nobody is
        // added twice, and the member unique key is never hit.
        val admitted = admission.admit(request.conversationId, request.userId)

        // The use is taken at approval, the moment the link actually lets someone in. A spent link
        // rolls the whole approval back, so the request stays PENDING rather than half-approved.
        if (admitted != null && request.inviteLinkId != null && !inviteLinkRepository.claimUse(request.inviteLinkId)) {
            throw BusinessException(ErrorCode.INVITE_LINK_MAX_USES)
        }
        return ApprovalOutcome(admitted)
    }

    @Transactional
    override fun rejectJoin(requestId: UUID, adminId: UUID) {
        val request = joinRequestRepository.findById(requestId)
            ?: throw BusinessException(ErrorCode.JOIN_REQUEST_NOT_FOUND)
        requireAdminOrOwner(request.conversationId, adminId)

        commitReview(request.reject(adminId, clock.instant()))
        log.info("Join request rejected: id={}, user={}, by={}", requestId, request.userId, adminId)
    }

    @Transactional(readOnly = true)
    override fun getPendingRequests(conversationId: UUID, requesterId: UUID): List<GroupJoinRequest> {
        requireAdminOrOwner(conversationId, requesterId)
        return joinRequestRepository.findPendingByConversationId(conversationId)
    }

    /** Applies a review transition, refusing it if the request was already reviewed — by anyone. */
    private fun commitReview(review: JoinRequestReview) {
        val reviewed = when (review) {
            is JoinRequestReview.AlreadyReviewed -> throw BusinessException(ErrorCode.JOIN_REQUEST_ALREADY_REVIEWED)
            is JoinRequestReview.Reviewed -> review.request
        }
        if (!joinRequestRepository.markReviewed(reviewed)) {
            throw BusinessException(ErrorCode.JOIN_REQUEST_ALREADY_REVIEWED)
        }
    }

    private fun requireAdminOrOwner(conversationId: UUID, userId: UUID) {
        val member = conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)
        if (member.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
    }
}
