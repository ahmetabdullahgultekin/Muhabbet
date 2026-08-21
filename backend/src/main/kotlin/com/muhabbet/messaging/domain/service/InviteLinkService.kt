package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ConversationMember
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.model.GroupInviteLink
import com.muhabbet.messaging.domain.model.GroupJoinRequest
import com.muhabbet.messaging.domain.model.JoinRequestStatus
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.`in`.ManageInviteLinkUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupInviteLinkRepository
import com.muhabbet.messaging.domain.port.out.GroupJoinRequestRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

open class InviteLinkService(
    private val inviteLinkRepository: GroupInviteLinkRepository,
    private val joinRequestRepository: GroupJoinRequestRepository,
    private val conversationRepository: ConversationRepository
) : ManageInviteLinkUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val TOKEN_BYTES = 32
    }

    @Transactional
    override fun createLink(
        conversationId: UUID,
        userId: UUID,
        requiresApproval: Boolean,
        maxUses: Int?,
        expiresAt: Instant?
    ): GroupInviteLink {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT)
        }

        requireAdminOrOwner(conversationId, userId)

        val token = generateSecureToken()
        val link = GroupInviteLink(
            conversationId = conversationId,
            inviteToken = token,
            createdBy = userId,
            requiresApproval = requiresApproval,
            maxUses = maxUses,
            expiresAt = expiresAt
        )

        val saved = inviteLinkRepository.save(link)
        log.info("Invite link created: conv={}, token={}", conversationId, token.take(8))
        return saved
    }

    /**
     * Reading the group's link needs **membership**, not admin rights, and that is a deliberate
     * split rather than an oversight (#705).
     *
     * Creating and revoking stay admin-or-owner because they are the policy acts: an admin decides
     * whether the group can be joined by link at all, and can withdraw that at any moment. Reading
     * is the distribution act, and reserving it to admins would collapse the whole feature into
     * `addMember`, which already exists and is already admin-gated — a member who cannot see the
     * link cannot invite anyone with it.
     *
     * It also costs nothing that is not already spent. [getLinkInfo] hands the same record to any
     * authenticated caller holding the token, member or not, so the link's contents are not
     * confidential to begin with; and once an admin shares the URL once it is transitively
     * shareable by whoever receives it. Withholding it from the group's own members would leave
     * them less trusted than a stranger who was forwarded the link.
     */
    @Transactional(readOnly = true)
    override fun getActiveLink(conversationId: UUID, userId: UUID): GroupInviteLink {
        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)

        // Membership before type, unlike createLink: a non-member should not learn from the error
        // code whether the id they guessed is a group or a DM.
        requireMember(conversationId, userId)

        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.GROUP_CANNOT_MODIFY_DIRECT)
        }

        // createLink does not deactivate the previous link, so a group whose admin pressed Create
        // twice has several active rows and the out-port returns them in whatever order the
        // database chose. The sheet shows one link and its Revoke button targets the one shown, so
        // the pick has to be deterministic: newest wins, id breaks a same-instant tie.
        return inviteLinkRepository.findActiveByConversationId(conversationId)
            .maxWithOrNull(compareBy({ it.createdAt }, { it.id }))
            ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)
    }

    @Transactional
    override fun revokeLink(linkId: UUID, userId: UUID) {
        val link = inviteLinkRepository.findById(linkId)
            ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)

        requireAdminOrOwner(link.conversationId, userId)
        inviteLinkRepository.deactivate(linkId)
        log.info("Invite link revoked: id={}", linkId)
    }

    @Transactional
    override fun joinViaLink(token: String, userId: UUID) {
        val link = inviteLinkRepository.findByToken(token)
            ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)

        if (link.expiresAt != null && link.expiresAt.isBefore(Instant.now())) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }

        if (link.maxUses != null && link.useCount >= link.maxUses) {
            throw BusinessException(ErrorCode.INVITE_LINK_MAX_USES)
        }

        // Check if already a member
        val existing = conversationRepository.findMember(link.conversationId, userId)
        if (existing != null) {
            throw BusinessException(ErrorCode.GROUP_ALREADY_MEMBER)
        }

        if (link.requiresApproval) {
            // Create a join request instead of directly adding
            val existingRequest = joinRequestRepository.findByConversationIdAndUserIdAndStatus(
                link.conversationId, userId, JoinRequestStatus.PENDING
            )
            if (existingRequest != null) {
                throw BusinessException(ErrorCode.JOIN_REQUEST_ALREADY_EXISTS)
            }

            joinRequestRepository.save(
                GroupJoinRequest(
                    conversationId = link.conversationId,
                    userId = userId,
                    inviteLinkId = link.id
                )
            )
            log.info("Join request created via invite link: conv={}, user={}", link.conversationId, userId)
        } else {
            // Directly add to conversation
            conversationRepository.saveMember(
                ConversationMember(conversationId = link.conversationId, userId = userId, role = MemberRole.MEMBER)
            )
            inviteLinkRepository.incrementUseCount(link.id)
            log.info("User joined via invite link: conv={}, user={}", link.conversationId, userId)
        }
    }

    @Transactional(readOnly = true)
    override fun getLinkInfo(token: String): GroupInviteLink {
        val link = inviteLinkRepository.findByToken(token)
            ?: throw BusinessException(ErrorCode.INVITE_LINK_NOT_FOUND)

        if (link.expiresAt != null && link.expiresAt.isBefore(Instant.now())) {
            throw BusinessException(ErrorCode.INVITE_LINK_EXPIRED)
        }

        return link
    }

    private fun requireMember(conversationId: UUID, userId: UUID): ConversationMember =
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)

    private fun requireAdminOrOwner(conversationId: UUID, userId: UUID) {
        if (requireMember(conversationId, userId).role == MemberRole.MEMBER) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
    }

    private fun generateSecureToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
