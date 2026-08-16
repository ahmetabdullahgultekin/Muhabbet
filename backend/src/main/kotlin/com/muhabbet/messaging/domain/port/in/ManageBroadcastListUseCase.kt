package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.BroadcastList
import java.util.UUID

/**
 * A broadcast list plus the one derived fact every caller needs about it.
 *
 * The count is not on [BroadcastList] because it is not part of the aggregate — it is an answer to
 * a question about the membership table, and putting it on the model would mean either loading the
 * members to build one or letting the model carry a field the repository has to remember to fill.
 */
data class BroadcastListSummary(
    val list: BroadcastList,
    val memberCount: Int
)

/**
 * A recipient with enough of a user record to be shown to a human.
 *
 * The membership row holds only ids. A screen listing raw UUIDs is not a recipient list, so the
 * name and avatar are resolved through [com.muhabbet.messaging.domain.port.out.UserDirectoryPort]
 * before the list leaves the domain. Both are nullable: a user who never set a display name is a
 * normal state, not an error.
 */
data class BroadcastListMemberSummary(
    val userId: UUID,
    val displayName: String?,
    val avatarUrl: String?
)

interface ManageBroadcastListUseCase {
    fun create(ownerId: UUID, name: String, memberIds: List<UUID>): BroadcastListSummary
    fun getByOwner(ownerId: UUID): List<BroadcastListSummary>
    fun getMembers(broadcastListId: UUID, ownerId: UUID): List<BroadcastListMemberSummary>
    fun addMembers(broadcastListId: UUID, ownerId: UUID, memberIds: List<UUID>): List<BroadcastListMemberSummary>
    fun removeMember(broadcastListId: UUID, ownerId: UUID, userId: UUID)
    fun delete(broadcastListId: UUID, ownerId: UUID)
}
