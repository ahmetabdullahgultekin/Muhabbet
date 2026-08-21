package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.CommunityMember
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.port.out.CommunityRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import java.util.UUID

/**
 * The three questions every community operation has to ask before it does anything: are you in this
 * community, do you run it, and do you own it.
 *
 * Extension functions on the out-port rather than private helpers on a service, because there are
 * now two services that must answer them **identically** — [CommunityService] and
 * [CommunityInviteService]. #375 was filed because a community endpoint got its authorisation wrong
 * once; the way that happens a second time is two copies of the check drifting apart, so there is
 * one copy.
 *
 * Every one of them reports the same `COMMUNITY_PERMISSION_DENIED` for "not a member" and for "not
 * allowed", deliberately: distinguishing them would tell a stranger whether a given community id
 * exists and who is in it, which is the disclosure the membership gate is there to prevent.
 */
internal fun CommunityRepository.requireMember(communityId: UUID, userId: UUID): CommunityMember =
    findMember(communityId, userId)
        ?: throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)

/** Admins and owners: rename, link groups, enrol people, mint and revoke invite links. */
internal fun CommunityRepository.requireAdminOrOwner(communityId: UUID, userId: UUID): CommunityMember {
    val member = requireMember(communityId, userId)
    if (!member.administers()) {
        throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)
    }
    return member
}

/** Owner only. Reserved for the irreversible operations — currently just delete. */
internal fun CommunityRepository.requireOwner(communityId: UUID, userId: UUID): CommunityMember {
    val member = requireMember(communityId, userId)
    if (member.role != MemberRole.OWNER) {
        throw BusinessException(ErrorCode.COMMUNITY_PERMISSION_DENIED)
    }
    return member
}
