package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.BroadcastList
import com.muhabbet.messaging.domain.model.BroadcastListMember
import com.muhabbet.messaging.domain.port.`in`.ManageBroadcastListUseCase
import com.muhabbet.messaging.domain.port.out.BroadcastListRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class BroadcastListService(
    private val broadcastListRepository: BroadcastListRepository
) : ManageBroadcastListUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun create(ownerId: UUID, name: String, memberIds: List<UUID>): BroadcastList {
        val list = BroadcastList(ownerId = ownerId, name = name)
        val saved = broadcastListRepository.save(list)

        memberIds.forEach { memberId ->
            broadcastListRepository.addMember(
                BroadcastListMember(broadcastListId = saved.id, userId = memberId)
            )
        }

        log.info("Broadcast list created: id={}, name={}, members={}", saved.id, name, memberIds.size)
        return saved
    }

    @Transactional(readOnly = true)
    override fun getByOwner(ownerId: UUID): List<BroadcastList> =
        broadcastListRepository.findByOwnerId(ownerId)

    @Transactional(readOnly = true)
    override fun getMembers(broadcastListId: UUID, ownerId: UUID): List<BroadcastListMember> {
        requireOwner(broadcastListId, ownerId)
        return broadcastListRepository.findMembers(broadcastListId)
    }

    @Transactional
    override fun addMembers(broadcastListId: UUID, ownerId: UUID, memberIds: List<UUID>): List<BroadcastListMember> {
        requireOwner(broadcastListId, ownerId)
        val existing = broadcastListRepository.findMembers(broadcastListId).map { it.userId }.toSet()

        return memberIds.filter { it !in existing }.map { memberId ->
            broadcastListRepository.addMember(
                BroadcastListMember(broadcastListId = broadcastListId, userId = memberId)
            )
        }
    }

    @Transactional
    override fun removeMember(broadcastListId: UUID, ownerId: UUID, userId: UUID) {
        requireOwner(broadcastListId, ownerId)
        broadcastListRepository.removeMember(broadcastListId, userId)
    }

    @Transactional
    override fun delete(broadcastListId: UUID, ownerId: UUID) {
        requireOwner(broadcastListId, ownerId)
        broadcastListRepository.delete(broadcastListId)
        log.info("Broadcast list deleted: id={}", broadcastListId)
    }

    private fun requireOwner(broadcastListId: UUID, ownerId: UUID) {
        val list = broadcastListRepository.findById(broadcastListId)
            ?: throw BusinessException(ErrorCode.BROADCAST_LIST_NOT_FOUND)
        if (list.ownerId != ownerId) {
            throw BusinessException(ErrorCode.BROADCAST_LIST_NOT_FOUND)
        }
    }
}
