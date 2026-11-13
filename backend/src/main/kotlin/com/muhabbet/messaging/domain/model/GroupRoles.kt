package com.muhabbet.messaging.domain.model

import java.util.UUID

/**
 * The rule every role change in a group must keep: **a group has exactly one OWNER** (#890).
 *
 * Role assignment used to be a raw setter. The owner could set `OWNER` on another member (two
 * owners, neither able to remove the other) or `MEMBER` on themselves (no owner, so nobody could
 * ever change a role again) — and the second state could only be repaired by hand in the database.
 * Both are now unreachable through [changeRole]: OWNER is never a target role and the owner is never
 * a target, so the count of owners cannot move. The single way ownership changes hands is
 * [transferOwnership], which swaps it in one step.
 *
 * Pure functions over the members involved, so the whole table is tested without a database; the
 * partial unique index from `V28__group_single_owner.sql` is the backstop if a future path forgets
 * to come through here.
 */
object GroupRoles {

    /** Why a role change or transfer was refused. Mapped to an `ErrorCode` by the service. */
    enum class Refusal {
        /** Only the owner may change roles or hand over ownership. */
        NOT_OWNER,

        /** The requested change would add or remove an owner; use [transferOwnership]. */
        OWNERSHIP_TRANSFER_REQUIRED
    }

    sealed interface RoleChange {
        data class Allowed(val target: UUID, val newRole: MemberRole) : RoleChange
        data class Refused(val refusal: Refusal) : RoleChange
    }

    sealed interface Transfer {
        /**
         * The swap. The outgoing owner must be demoted (to [PREVIOUS_OWNER_ROLE]) before the new
         * one is promoted, so at no point does the table hold two OWNER rows for the group.
         */
        data class Allowed(val previousOwner: UUID, val newOwner: UUID) : Transfer

        data class Refused(val refusal: Refusal) : Transfer

        companion object {
            /** An owner who hands the group over stays on as an admin, as in WhatsApp. */
            val PREVIOUS_OWNER_ROLE = MemberRole.ADMIN
        }
    }

    /**
     * May [actor] give [target] the role [newRole]?
     *
     * The owner may move anyone *else* between MEMBER and ADMIN. Everything that would change who
     * the owner is — promoting to OWNER, or demoting the owner, themselves included — is refused
     * with [Refusal.OWNERSHIP_TRANSFER_REQUIRED].
     */
    fun changeRole(actor: ConversationMember, target: ConversationMember, newRole: MemberRole): RoleChange {
        if (actor.role != MemberRole.OWNER) return RoleChange.Refused(Refusal.NOT_OWNER)
        if (newRole == MemberRole.OWNER || target.role == MemberRole.OWNER) {
            return RoleChange.Refused(Refusal.OWNERSHIP_TRANSFER_REQUIRED)
        }
        return RoleChange.Allowed(target.userId, newRole)
    }

    /**
     * May [actor] hand ownership to [target]? Only the owner may, and only to another member —
     * handing it to yourself would be a no-op dressed up as a change.
     */
    fun transferOwnership(actor: ConversationMember, target: ConversationMember): Transfer {
        if (actor.role != MemberRole.OWNER) return Transfer.Refused(Refusal.NOT_OWNER)
        if (actor.userId == target.userId) return Transfer.Refused(Refusal.OWNERSHIP_TRANSFER_REQUIRED)
        return Transfer.Allowed(previousOwner = actor.userId, newOwner = target.userId)
    }

    /**
     * Who inherits the group when its owner [leaverId] leaves: the longest-standing admin, else the
     * longest-standing member, ties broken by user id so two instances agree. Null when the owner
     * was the last member.
     */
    fun successorOnLeave(members: List<ConversationMember>, leaverId: UUID): ConversationMember? {
        val others = members.filter { it.userId != leaverId }
            .sortedWith(compareBy<ConversationMember>({ it.joinedAt }, { it.userId }))
        return others.firstOrNull { it.role == MemberRole.ADMIN } ?: others.firstOrNull()
    }
}
