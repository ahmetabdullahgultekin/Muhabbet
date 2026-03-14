package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.BroadcastListJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.entity.BroadcastListMemberJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataBroadcastListJpaRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataBroadcastListMemberRepository
import com.muhabbet.messaging.domain.model.BroadcastList
import com.muhabbet.messaging.domain.model.BroadcastListMember
import com.muhabbet.messaging.domain.port.out.BroadcastListRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class BroadcastListPersistenceAdapter(
    private val listRepo: SpringDataBroadcastListJpaRepository,
    private val memberRepo: SpringDataBroadcastListMemberRepository
) : BroadcastListRepository {

    override fun save(list: BroadcastList): BroadcastList =
        listRepo.save(BroadcastListJpaEntity.fromDomain(list)).toDomain()

    override fun findById(id: UUID): BroadcastList? =
        listRepo.findById(id).orElse(null)?.toDomain()

    override fun findByOwnerId(ownerId: UUID): List<BroadcastList> =
        listRepo.findByOwnerId(ownerId).map { it.toDomain() }

    override fun delete(id: UUID) =
        listRepo.deleteById(id)

    override fun addMember(member: BroadcastListMember): BroadcastListMember =
        memberRepo.save(BroadcastListMemberJpaEntity.fromDomain(member)).toDomain()

    override fun removeMember(broadcastListId: UUID, userId: UUID) =
        memberRepo.deleteByBroadcastListIdAndUserId(broadcastListId, userId)

    override fun findMembers(broadcastListId: UUID): List<BroadcastListMember> =
        memberRepo.findByBroadcastListId(broadcastListId).map { it.toDomain() }
}
