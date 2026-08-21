package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.CommunityInviteLink
import java.util.UUID

interface CommunityInviteLinkRepository {
    fun save(link: CommunityInviteLink): CommunityInviteLink
    fun findById(id: UUID): CommunityInviteLink?

    /**
     * Resolves a token to its link, active or not.
     *
     * Deliberately not filtered to active rows: a revoked link must be distinguishable from a token
     * that never existed at the service layer, so the two can be reported differently to the holder.
     */
    fun findByToken(token: String): CommunityInviteLink?

    /** Every link an admin could still revoke — active rows only, newest first. */
    fun findActiveByCommunityId(communityId: UUID): List<CommunityInviteLink>

    /** Active link count for one community, used to cap how many an admin may mint. */
    fun countActiveByCommunityId(communityId: UUID): Int

    fun deactivate(id: UUID)

    /** Bumps `use_count` by one. Called only after a join is actually written. */
    fun incrementUseCount(id: UUID)
}
