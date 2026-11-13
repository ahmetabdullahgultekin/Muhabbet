package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.GroupEvent
import com.muhabbet.messaging.domain.model.GroupEventRsvp
import com.muhabbet.messaging.domain.model.GroupEventSummary
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.RsvpStatus
import com.muhabbet.messaging.domain.port.`in`.ManageGroupEventUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupEventRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class GroupEventService(
    private val groupEventRepository: GroupEventRepository,
    private val conversationRepository: ConversationRepository,
    private val transactions: TransactionRunner
) : ManageGroupEventUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun createEvent(
        conversationId: UUID,
        userId: UUID,
        title: String,
        description: String?,
        eventTime: Instant,
        location: String?
    ): GroupEventSummary = transactions.inTransaction {
        requireMember(conversationId, userId)

        val event = GroupEvent(
            conversationId = conversationId,
            createdBy = userId,
            title = title,
            description = description,
            eventTime = eventTime,
            location = location
        )
        val saved = groupEventRepository.save(event)
        log.info("Group event created: id={}, conv={}, title={}", saved.id, conversationId, title)
        // Nobody can have answered an event that did not exist a moment ago.
        return@inTransaction GroupEventSummary(saved, goingCount = 0)
    }

    override fun getEvents(conversationId: UUID): List<GroupEventSummary> = transactions.inReadOnlyTransaction {
        val events = groupEventRepository.findByConversationId(conversationId)
        val goingCounts = groupEventRepository.countGoingRsvps(events.map { it.id })
        return@inReadOnlyTransaction events.map { GroupEventSummary(it, goingCounts[it.id] ?: 0) }
    }

    override fun deleteEvent(eventId: UUID, userId: UUID) {
        transactions.inTransaction<Unit> {
            val event = groupEventRepository.findById(eventId)
                ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)

            // Only creator or admin/owner can delete
            if (event.createdBy != userId) {
                val member = conversationRepository.findMember(event.conversationId, userId)
                    ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)
                if (member.role == MemberRole.MEMBER) {
                    throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
                }
            }

            groupEventRepository.delete(eventId)
            log.info("Group event deleted: id={}", eventId)
        }
    }

    override fun rsvp(eventId: UUID, userId: UUID, status: RsvpStatus): GroupEventRsvp = transactions.inTransaction {
        val event = groupEventRepository.findById(eventId)
            ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)

        requireMember(event.conversationId, userId)

        val rsvp = GroupEventRsvp(eventId = eventId, userId = userId, status = status)
        return@inTransaction groupEventRepository.saveRsvp(rsvp)
    }

    override fun getRsvps(eventId: UUID): List<GroupEventRsvp> = transactions.inReadOnlyTransaction {
        groupEventRepository.findRsvpsByEventId(eventId)
    }

    private fun requireMember(conversationId: UUID, userId: UUID) {
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)
    }
}
