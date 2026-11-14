package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.GroupInviteLink
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

    /**
     * Admits the requester, at most once (#788).
     *
     * Guards that were missing before the app had a caller for this:
     * - the request was never checked against the group in the URL;
     * - its status was never checked, so a rejected request could be "approved" later and an
     *   approved one approved again;
     * - a requester who had meanwhile been added by hand — or promoted to admin since — was written
     *   back as a plain MEMBER, silently demoting them. (`conversation_members` has a composite
     *   primary key, so a *duplicate* row was never possible; the overwrite was.)
     * - the link the request came through is re-checked: it must be this group's, and approving
     *   must not take it past `maxUses`. Without the second, a link limited to 10 uses with 30
     *   pending requests admits all 30.
     *
     * A request in another group, no longer pending, or carrying another group's link is reported
     * as not found, which is also what it is from this group's point of view.
     */
    @Transactional
    override fun approveJoin(conversationId: UUID, requestId: UUID, adminId: UUID) {
        val request = requirePending(conversationId, requestId)
        requireAdminOrOwner(request.conversationId, adminId)
        val link = request.inviteLinkId?.let { requireLinkWithUsesLeft(request.conversationId, it) }

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

        if (link != null) {
            inviteLinkRepository.incrementUseCount(link.id)
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

    private fun requireLinkWithUsesLeft(conversationId: UUID, linkId: UUID): GroupInviteLink {
        val link = inviteLinkRepository.findById(linkId)
        if (link == null || link.conversationId != conversationId) {
            throw BusinessException(ErrorCode.JOIN_REQUEST_NOT_FOUND)
        }
        if (link.maxUses != null && link.useCount >= link.maxUses) {
            throw BusinessException(ErrorCode.INVITE_LINK_MAX_USES)
        }
        return link
    }

    private fun requireAdminOrOwner(conversationId: UUID, userId: UUID) {
        val member = conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)
        if (member.role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
    }
}
