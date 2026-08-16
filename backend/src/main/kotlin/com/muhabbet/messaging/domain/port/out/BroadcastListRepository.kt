package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.BroadcastList
import com.muhabbet.messaging.domain.model.BroadcastListMember
import java.util.UUID

interface BroadcastListRepository {
    fun save(list: BroadcastList): BroadcastList
    fun findById(id: UUID): BroadcastList?
    fun findByOwnerId(ownerId: UUID): List<BroadcastList>
    fun delete(id: UUID)

    fun addMember(member: BroadcastListMember): BroadcastListMember
    fun removeMember(broadcastListId: UUID, userId: UUID)
    fun findMembers(broadcastListId: UUID): List<BroadcastListMember>

    /**
     * Recipient counts for a whole page of lists in one query.
     *
     * Batched by contract: the list screen shows a count on every row, and resolving them by
     * calling [findMembers] per list is the N+1 the shape of this signature exists to prevent.
     * Lists with no recipients are absent from the map rather than present with zero.
     */
    fun countMembersByListIds(listIds: List<UUID>): Map<UUID, Int>
}
