package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.CommunityGroup
import com.muhabbet.messaging.domain.model.ConversationType
import com.muhabbet.messaging.domain.port.`in`.ManageCommunityGroupsUseCase
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.util.UUID

/** Linking a community's groups, and unlinking them (#894). */
class CommunityGroupService(
    private val communityRepository: CommunityRepository,
    private val conversationRepository: ConversationRepository,
    private val transactions: TransactionRunner
) : ManageCommunityGroupsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val access = CommunityAccess(communityRepository)

    override fun addGroup(communityId: UUID, conversationId: UUID, userId: UUID): CommunityGroup = transactions.inTransaction {
        access.requireAdminOrOwner(communityId, userId)

        // The conversation id is supplied by the caller, so it is checked against the caller before
        // anything is stored: linking a conversation publishes its name, avatar and member count to
        // everyone who can read the community. The membership lookup deliberately comes first, so a
        // caller who is not in the conversation gets the same answer whether or not it exists.
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)

        val conversation = conversationRepository.findById(conversationId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_FOUND)
        if (conversation.type != ConversationType.GROUP) {
            throw BusinessException(ErrorCode.COMMUNITY_NOT_A_GROUP)
        }

        val group = CommunityGroup(communityId = communityId, conversationId = conversationId)
        val saved = communityRepository.addGroup(group)
        log.info("Group added to community: community={}, conversation={}", communityId, conversationId)
        return@inTransaction saved
    }

    override fun removeGroup(communityId: UUID, conversationId: UUID, userId: UUID) {
        transactions.inTransaction<Unit> {
            access.requireAdminOrOwner(communityId, userId)
            communityRepository.removeGroup(communityId, conversationId)
            log.info("Group removed from community: community={}, conversation={}", communityId, conversationId)
        }
    }
}
