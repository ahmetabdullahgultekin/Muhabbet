package com.muhabbet.app.ui.group

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
