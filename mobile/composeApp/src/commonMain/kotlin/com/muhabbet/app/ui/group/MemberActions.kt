package com.muhabbet.app.ui.group

import com.muhabbet.shared.dto.JoinRequestResponse
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.MemberRole

/** What the viewer may do to one member of a group, in the order the sheet lists them. */
enum class MemberAction { VIEW_PROFILE, MAKE_ADMIN, DISMISS_ADMIN, REMOVE }

/**
 * The member-management policy, as one pure function (#788).
 *
 * Group info grew a delete button on every row an admin could see, with no confirmation, and the
 * role endpoint had no caller at all. This is the single statement of who may do what, mirroring
 * what `GroupService` enforces — the client offers exactly what the server will accept, so no
 * action is a button that always fails:
 *
 * - **Roles** change only by the owner (`persistMemberRole` refuses anyone else), and only between
 *   MEMBER and ADMIN. Ownership transfer is not offered: the server would accept OWNER as a target
 *   role and leave the group with two owners, which nothing else in the app models.
 * - **Removal** (`requireCanRemove`): the owner may remove anyone but themselves; an admin may
 *   remove plain members only — not another admin; nobody removes the owner.
 * - Nothing is offered on your own row: leaving has its own confirmed action in the top bar.
 */
fun memberActions(myRole: MemberRole, targetRole: MemberRole, isSelf: Boolean): List<MemberAction> {
    if (isSelf) return emptyList()
    return buildList {
        add(MemberAction.VIEW_PROFILE)
        if (myRole == MemberRole.OWNER) {
            when (targetRole) {
                MemberRole.MEMBER -> add(MemberAction.MAKE_ADMIN)
                MemberRole.ADMIN -> add(MemberAction.DISMISS_ADMIN)
                MemberRole.OWNER -> Unit
            }
        }
        val canRemove = targetRole != MemberRole.OWNER && when (myRole) {
            MemberRole.OWNER -> true
            MemberRole.ADMIN -> targetRole == MemberRole.MEMBER
            MemberRole.MEMBER -> false
        }
        if (canRemove) add(MemberAction.REMOVE)
    }
}

/** Whether the viewer may see and answer join requests — the server's `requireAdminOrOwner`. */
fun canReviewJoinRequests(myRole: MemberRole): Boolean = myRole != MemberRole.MEMBER

/**
 * The member list after [request] was approved (#788 review).
 *
 * The screen used to re-read the first page of the conversation list after an approval and treat a
 * failure of that read as a failed approval: the admin saw an error, the row stayed, and pressing
 * Approve again got `JOIN_REQUEST_NOT_FOUND` because the request was no longer pending — a row that
 * could never be cleared. An admin with more than one page of chats also silently kept the old
 * member list. The approval's outcome is the approve call alone, and what it changes is known
 * without asking: the server admits the requester as a plain MEMBER, unless they were already in
 * the group, in which case it leaves their row (and role) as it was.
 *
 * The admitted row carries what the request carries — name and avatar, never a phone number, which
 * the request deliberately withholds — and the next full load of the screen fills in the rest.
 * Online state is unknown here and shown as offline rather than guessed.
 */
fun withAdmittedRequester(
    participants: List<ParticipantResponse>,
    request: JoinRequestResponse
): List<ParticipantResponse> {
    if (participants.any { it.userId == request.userId }) return participants
    return participants + ParticipantResponse(
        userId = request.userId,
        displayName = request.displayName,
        avatarUrl = request.avatarUrl,
        role = MemberRole.MEMBER,
        isOnline = false
    )
}
