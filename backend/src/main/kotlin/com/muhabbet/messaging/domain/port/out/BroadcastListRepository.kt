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
}
