package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.BroadcastList
import com.muhabbet.messaging.domain.model.BroadcastListMember
import java.util.UUID

interface ManageBroadcastListUseCase {
    fun create(ownerId: UUID, name: String, memberIds: List<UUID>): BroadcastList
    fun getByOwner(ownerId: UUID): List<BroadcastList>
    fun getMembers(broadcastListId: UUID, ownerId: UUID): List<BroadcastListMember>
    fun addMembers(broadcastListId: UUID, ownerId: UUID, memberIds: List<UUID>): List<BroadcastListMember>
    fun removeMember(broadcastListId: UUID, ownerId: UUID, userId: UUID)
    fun delete(broadcastListId: UUID, ownerId: UUID)
}
