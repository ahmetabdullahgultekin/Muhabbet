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
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.util.UUID

class BroadcastListService(
    private val broadcastListRepository: BroadcastListRepository,
    private val userDirectoryPort: UserDirectoryPort,
    private val transactions: TransactionRunner
) : ManageBroadcastListUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun create(ownerId: UUID, name: String, memberIds: List<UUID>): BroadcastListSummary = transactions.inTransaction {
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
        return@inTransaction BroadcastListSummary(list = saved, memberCount = distinctMemberIds.size)
    }

    override fun getByOwner(ownerId: UUID): List<BroadcastListSummary> = transactions.inReadOnlyTransaction {
        val lists = broadcastListRepository.findByOwnerId(ownerId)
        if (lists.isEmpty()) return@inReadOnlyTransaction emptyList()

        // One count query for the page, not one per row.
        val counts = broadcastListRepository.countMembersByListIds(lists.map { it.id })
        return@inReadOnlyTransaction lists.map { BroadcastListSummary(list = it, memberCount = counts[it.id] ?: 0) }
    }

    override fun getMembers(broadcastListId: UUID, ownerId: UUID): List<BroadcastListMemberSummary> = transactions.inReadOnlyTransaction {
        requireOwner(broadcastListId, ownerId)
        return@inReadOnlyTransaction withDisplayInfo(broadcastListRepository.findMembers(broadcastListId))
    }

    override fun addMembers(
        broadcastListId: UUID,
        ownerId: UUID,
        memberIds: List<UUID>
    ): List<BroadcastListMemberSummary> = transactions.inTransaction {
        requireOwner(broadcastListId, ownerId)
        val existing = broadcastListRepository.findMembers(broadcastListId).map { it.userId }.toSet()

        val added = memberIds.distinct().filter { it !in existing }.map { memberId ->
            broadcastListRepository.addMember(
                BroadcastListMember(broadcastListId = broadcastListId, userId = memberId)
            )
        }
        return@inTransaction withDisplayInfo(added)
    }

    override fun removeMember(broadcastListId: UUID, ownerId: UUID, userId: UUID) {
        transactions.inTransaction<Unit> {
            requireOwner(broadcastListId, ownerId)
            broadcastListRepository.removeMember(broadcastListId, userId)
        }
    }

    override fun delete(broadcastListId: UUID, ownerId: UUID) {
        transactions.inTransaction<Unit> {
            requireOwner(broadcastListId, ownerId)
            broadcastListRepository.delete(broadcastListId)
            log.info("Broadcast list deleted: id={}", broadcastListId)
        }
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
