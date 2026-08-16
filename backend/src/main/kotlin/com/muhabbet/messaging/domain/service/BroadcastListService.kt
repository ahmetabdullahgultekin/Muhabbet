package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.BroadcastList
import com.muhabbet.messaging.domain.model.BroadcastListMember
import com.muhabbet.messaging.domain.port.`in`.BroadcastListMemberSummary
import com.muhabbet.messaging.domain.port.`in`.BroadcastListSummary
import com.muhabbet.messaging.domain.port.`in`.ManageBroadcastListUseCase
import com.muhabbet.messaging.domain.port.out.BroadcastListRepository
import com.muhabbet.messaging.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class BroadcastListService(
    private val broadcastListRepository: BroadcastListRepository,
    private val userDirectoryPort: UserDirectoryPort
) : ManageBroadcastListUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun create(ownerId: UUID, name: String, memberIds: List<UUID>): BroadcastListSummary {
        val list = BroadcastList(ownerId = ownerId, name = name)
        val saved = broadcastListRepository.save(list)

        // Duplicates in the request would otherwise become duplicate rows and inflate the count.
        val distinctMemberIds = memberIds.distinct()
        distinctMemberIds.forEach { memberId ->
            broadcastListRepository.addMember(
                BroadcastListMember(broadcastListId = saved.id, userId = memberId)
            )
        }

        log.info("Broadcast list created: id={}, name={}, members={}", saved.id, name, distinctMemberIds.size)
        return BroadcastListSummary(list = saved, memberCount = distinctMemberIds.size)
    }

    @Transactional(readOnly = true)
    override fun getByOwner(ownerId: UUID): List<BroadcastListSummary> {
        val lists = broadcastListRepository.findByOwnerId(ownerId)
        if (lists.isEmpty()) return emptyList()

        // One count query for the page, not one per row.
        val counts = broadcastListRepository.countMembersByListIds(lists.map { it.id })
        return lists.map { BroadcastListSummary(list = it, memberCount = counts[it.id] ?: 0) }
    }

    @Transactional(readOnly = true)
    override fun getMembers(broadcastListId: UUID, ownerId: UUID): List<BroadcastListMemberSummary> {
        requireOwner(broadcastListId, ownerId)
        return withDisplayInfo(broadcastListRepository.findMembers(broadcastListId))
    }

    @Transactional
    override fun addMembers(
        broadcastListId: UUID,
        ownerId: UUID,
        memberIds: List<UUID>
    ): List<BroadcastListMemberSummary> {
        requireOwner(broadcastListId, ownerId)
        val existing = broadcastListRepository.findMembers(broadcastListId).map { it.userId }.toSet()

        val added = memberIds.distinct().filter { it !in existing }.map { memberId ->
            broadcastListRepository.addMember(
                BroadcastListMember(broadcastListId = broadcastListId, userId = memberId)
            )
        }
        return withDisplayInfo(added)
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

    /** Puts a name and a face on membership rows that hold nothing but ids. One lookup per call. */
    private fun withDisplayInfo(members: List<BroadcastListMember>): List<BroadcastListMemberSummary> {
        if (members.isEmpty()) return emptyList()

        val displayInfo = userDirectoryPort.findDisplayInfo(members.map { it.userId })
        return members.map { member ->
            val info = displayInfo[member.userId]
            BroadcastListMemberSummary(
                userId = member.userId,
                displayName = info?.displayName,
                avatarUrl = info?.avatarUrl
            )
        }
    }

    private fun requireOwner(broadcastListId: UUID, ownerId: UUID) {
        val list = broadcastListRepository.findById(broadcastListId)
            ?: throw BusinessException(ErrorCode.BROADCAST_LIST_NOT_FOUND)
        if (list.ownerId != ownerId) {
            throw BusinessException(ErrorCode.BROADCAST_LIST_NOT_FOUND)
        }
    }
}
