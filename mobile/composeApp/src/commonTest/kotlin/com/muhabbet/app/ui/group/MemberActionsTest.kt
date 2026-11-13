package com.muhabbet.app.ui.group

import com.muhabbet.shared.model.MemberRole
import com.muhabbet.shared.model.MemberRole.ADMIN
import com.muhabbet.shared.model.MemberRole.MEMBER
import com.muhabbet.shared.model.MemberRole.OWNER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #788: every (viewer role, target role) pair, against what `GroupService` actually accepts.
 *
 * The screen used to offer a remove button to an admin on another admin's row — a button the server
 * refuses (`requireCanRemove`) — and offered no role change at all, although the owner may make
 * and dismiss admins.
 */
class MemberActionsTest {

    private val profile = MemberAction.VIEW_PROFILE

    private fun actions(me: MemberRole, target: MemberRole) = memberActions(me, target, isSelf = false)

    @Test
    fun should_letTheOwnerPromoteAndRemoveAPlainMember() {
        assertEquals(listOf(profile, MemberAction.MAKE_ADMIN, MemberAction.REMOVE), actions(OWNER, MEMBER))
    }

    @Test
    fun should_letTheOwnerDismissAndRemoveAnAdmin() {
        assertEquals(listOf(profile, MemberAction.DISMISS_ADMIN, MemberAction.REMOVE), actions(OWNER, ADMIN))
    }

    @Test
    fun should_letAnAdminRemoveAPlainMemberButNotChangeRoles() {
        assertEquals(listOf(profile, MemberAction.REMOVE), actions(ADMIN, MEMBER))
    }

    @Test
    fun should_notLetAnAdminRemoveAnotherAdmin() {
        assertEquals(listOf(profile), actions(ADMIN, ADMIN))
    }

    @Test
    fun should_notLetAnyoneRemoveOrDemoteTheOwner() {
        listOf(OWNER, ADMIN, MEMBER).forEach { me ->
            assertEquals(listOf(profile), actions(me, OWNER), "viewer $me")
        }
    }

    @Test
    fun should_giveAPlainMemberOnlyTheProfile() {
        listOf(OWNER, ADMIN, MEMBER).forEach { target ->
            assertEquals(listOf(profile), actions(MEMBER, target), "target $target")
        }
    }

    @Test
    fun should_offerNothingOnYourOwnRow() {
        listOf(OWNER, ADMIN, MEMBER).forEach { me ->
            assertEquals(emptyList(), memberActions(me, me, isSelf = true))
        }
    }

    @Test
    fun should_letOnlyAdminsAndTheOwnerReviewJoinRequests() {
        assertTrue(canReviewJoinRequests(OWNER))
        assertTrue(canReviewJoinRequests(ADMIN))
        assertFalse(canReviewJoinRequests(MEMBER))
    }
}
