package com.muhabbet.messaging.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.Instant
import java.util.UUID
import java.util.stream.Stream

/**
 * The whole role-change table of #890. Every row that is not "owner moves someone else between
 * MEMBER and ADMIN" must be refused, because every other row changes how many owners the group has.
 */
class GroupRolesTest {

    private val group = UUID.randomUUID()
    private val ownerId = UUID.randomUUID()

    private fun m(id: UUID, role: MemberRole, joinedAt: Instant = Instant.EPOCH) =
        ConversationMember(conversationId = group, userId = id, role = role, joinedAt = joinedAt)

    companion object {
        @JvmStatic
        fun table(): Stream<Arguments> {
            val allowed: GroupRoles.Refusal? = null
            return Stream.of(
                // actor, target, newRole, refusal (null = allowed)
                Arguments.of(MemberRole.OWNER, MemberRole.MEMBER, MemberRole.ADMIN, allowed),
                Arguments.of(MemberRole.OWNER, MemberRole.ADMIN, MemberRole.MEMBER, allowed),
                Arguments.of(MemberRole.OWNER, MemberRole.MEMBER, MemberRole.MEMBER, allowed),
                Arguments.of(MemberRole.OWNER, MemberRole.MEMBER, MemberRole.OWNER, GroupRoles.Refusal.OWNERSHIP_TRANSFER_REQUIRED),
                Arguments.of(MemberRole.OWNER, MemberRole.ADMIN, MemberRole.OWNER, GroupRoles.Refusal.OWNERSHIP_TRANSFER_REQUIRED),
                Arguments.of(MemberRole.ADMIN, MemberRole.MEMBER, MemberRole.ADMIN, GroupRoles.Refusal.NOT_OWNER),
                Arguments.of(MemberRole.ADMIN, MemberRole.ADMIN, MemberRole.MEMBER, GroupRoles.Refusal.NOT_OWNER),
                Arguments.of(MemberRole.ADMIN, MemberRole.MEMBER, MemberRole.OWNER, GroupRoles.Refusal.NOT_OWNER),
                Arguments.of(MemberRole.MEMBER, MemberRole.MEMBER, MemberRole.ADMIN, GroupRoles.Refusal.NOT_OWNER),
                Arguments.of(MemberRole.MEMBER, MemberRole.OWNER, MemberRole.MEMBER, GroupRoles.Refusal.NOT_OWNER)
            )
        }
    }

    @ParameterizedTest(name = "{0} sets {1} to {2} -> {3}")
    @MethodSource("table")
    fun `should apply the single-owner role table`(
        actorRole: MemberRole,
        targetRole: MemberRole,
        newRole: MemberRole,
        refusal: GroupRoles.Refusal?
    ) {
        val target = m(UUID.randomUUID(), targetRole)
        val decision = GroupRoles.changeRole(m(UUID.randomUUID(), actorRole), target, newRole)

        val expected = refusal?.let { GroupRoles.RoleChange.Refused(it) }
            ?: GroupRoles.RoleChange.Allowed(target.userId, newRole)
        assertEquals(expected, decision)
    }

    @Test
    fun `should refuse the owner demoting themselves when that would leave the group ownerless`() {
        val owner = m(ownerId, MemberRole.OWNER)

        assertEquals(
            GroupRoles.RoleChange.Refused(GroupRoles.Refusal.OWNERSHIP_TRANSFER_REQUIRED),
            GroupRoles.changeRole(owner, owner, MemberRole.MEMBER)
        )
        assertEquals(
            GroupRoles.RoleChange.Refused(GroupRoles.Refusal.OWNERSHIP_TRANSFER_REQUIRED),
            GroupRoles.changeRole(owner, owner, MemberRole.ADMIN)
        )
    }

    @Test
    fun `should allow the owner to transfer to another member`() {
        val target = UUID.randomUUID()

        assertEquals(
            GroupRoles.Transfer.Allowed(previousOwner = ownerId, newOwner = target),
            GroupRoles.transferOwnership(m(ownerId, MemberRole.OWNER), m(target, MemberRole.MEMBER))
        )
    }

    @Test
    fun `should refuse a transfer by anyone but the owner`() {
        assertEquals(
            GroupRoles.Transfer.Refused(GroupRoles.Refusal.NOT_OWNER),
            GroupRoles.transferOwnership(m(UUID.randomUUID(), MemberRole.ADMIN), m(ownerId, MemberRole.OWNER))
        )
    }

    @Test
    fun `should refuse the owner transferring to themselves`() {
        val owner = m(ownerId, MemberRole.OWNER)

        assertEquals(
            GroupRoles.Transfer.Refused(GroupRoles.Refusal.OWNERSHIP_TRANSFER_REQUIRED),
            GroupRoles.transferOwnership(owner, owner)
        )
    }

    @Test
    fun `should hand the group to the longest-standing admin before any member`() {
        val oldMember = m(UUID.randomUUID(), MemberRole.MEMBER, Instant.parse("2026-01-01T00:00:00Z"))
        val newerAdmin = m(UUID.randomUUID(), MemberRole.ADMIN, Instant.parse("2026-02-01T00:00:00Z"))
        val olderAdmin = m(UUID.randomUUID(), MemberRole.ADMIN, Instant.parse("2026-01-15T00:00:00Z"))

        val successor = GroupRoles.successorOnLeave(
            listOf(m(ownerId, MemberRole.OWNER), oldMember, newerAdmin, olderAdmin),
            ownerId
        )

        assertEquals(olderAdmin, successor)
    }

    @Test
    fun `should hand the group to the longest-standing member when there is no admin`() {
        val older = m(UUID.randomUUID(), MemberRole.MEMBER, Instant.parse("2026-01-01T00:00:00Z"))
        val newer = m(UUID.randomUUID(), MemberRole.MEMBER, Instant.parse("2026-03-01T00:00:00Z"))

        assertEquals(older, GroupRoles.successorOnLeave(listOf(newer, m(ownerId, MemberRole.OWNER), older), ownerId))
    }

    @Test
    fun `should have no successor when the owner was the last member`() {
        assertNull(GroupRoles.successorOnLeave(listOf(m(ownerId, MemberRole.OWNER)), ownerId))
    }
}
