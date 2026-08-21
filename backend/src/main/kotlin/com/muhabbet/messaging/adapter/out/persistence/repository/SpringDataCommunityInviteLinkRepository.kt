package com.muhabbet.messaging.adapter.out.persistence.repository

import com.muhabbet.messaging.adapter.out.persistence.entity.CommunityInviteLinkJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SpringDataCommunityInviteLinkRepository : JpaRepository<CommunityInviteLinkJpaEntity, UUID> {

    /**
     * Deliberately **not** `...AndIsActiveTrue`, unlike the group equivalent.
     *
     * `CommunityInviteLinkRepository.findByToken` promises to resolve revoked links too, so that the
     * service can decide what a revoked token means rather than having the query silently turn it
     * into "no such token". The service currently reports both as `INVITE_LINK_NOT_FOUND`, but that
     * is its decision to change; hiding the row here would take the decision away from it.
     */
    fun findByInviteToken(inviteToken: String): CommunityInviteLinkJpaEntity?

    /** Newest first, so the admin's revoke list does not reshuffle between opens. */
    fun findByCommunityIdAndIsActiveTrueOrderByCreatedAtDesc(
        communityId: UUID
    ): List<CommunityInviteLinkJpaEntity>

    fun countByCommunityIdAndIsActiveTrue(communityId: UUID): Int
}
