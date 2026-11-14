package com.muhabbet.app.ui.group

import com.muhabbet.shared.dto.JoinRequestResponse
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.MemberRole.ADMIN
import com.muhabbet.shared.model.MemberRole.MEMBER
import com.muhabbet.shared.model.MemberRole.OWNER
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * #788 review: an approval's effect on the member list, derived without a follow-up read. The
 * screen used to re-read the conversation list inside the same failure scope as the approval, so a
 * network blip on that read reported an approval that had succeeded as a failure and left a row
 * that could never be cleared.
 */
class WithAdmittedRequesterTest {

    private val owner = ParticipantResponse(userId = "u-owner", displayName = "Sahip", role = OWNER, isOnline = true)

    private fun request(userId: String = "u-new") = JoinRequestResponse(
        id = "r-1",
        userId = userId,
        displayName = "Ayşe",
        avatarUrl = "https://cdn/a.jpg",
        status = "PENDING",
        createdAt = "2026-11-13T00:00:00Z"
    )

    @Test
    fun should_appendTheRequesterAsAPlainMember_when_approved() {
        val after = withAdmittedRequester(listOf(owner), request())

        assertEquals(listOf("u-owner", "u-new"), after.map { it.userId })
        val admitted = after.last()
        assertEquals(MEMBER, admitted.role)
        assertEquals("Ayşe", admitted.displayName)
        assertEquals("https://cdn/a.jpg", admitted.avatarUrl)
        assertNull(admitted.phoneNumber)
        assertFalse(admitted.isOnline)
    }

    @Test
    fun should_leaveAnExistingRowAndItsRoleAlone_when_theRequesterWasAlreadyAMember() {
        val alreadyAdmin = ParticipantResponse(userId = "u-new", displayName = "Ayşe", role = ADMIN, isOnline = true)
        val before = listOf(owner, alreadyAdmin)

        assertEquals(before, withAdmittedRequester(before, request()))
    }
}
