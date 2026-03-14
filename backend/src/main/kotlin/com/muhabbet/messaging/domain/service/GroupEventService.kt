package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.GroupEvent
import com.muhabbet.messaging.domain.model.GroupEventRsvp
import com.muhabbet.messaging.domain.model.MemberRole
import com.muhabbet.messaging.domain.model.RsvpStatus
import com.muhabbet.messaging.domain.port.`in`.ManageGroupEventUseCase
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.GroupEventRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

open class GroupEventService(
    private val groupEventRepository: GroupEventRepository,
    private val conversationRepository: ConversationRepository
) : ManageGroupEventUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun createEvent(
        conversationId: UUID,
        userId: UUID,
        title: String,
        description: String?,
        eventTime: Instant,
        location: String?
    ): GroupEvent {
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
        return saved
    }

    @Transactional(readOnly = true)
    override fun getEvents(conversationId: UUID): List<GroupEvent> =
        groupEventRepository.findByConversationId(conversationId)

    @Transactional
    override fun deleteEvent(eventId: UUID, userId: UUID) {
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

    @Transactional
    override fun rsvp(eventId: UUID, userId: UUID, status: RsvpStatus): GroupEventRsvp {
        val event = groupEventRepository.findById(eventId)
            ?: throw BusinessException(ErrorCode.EVENT_NOT_FOUND)

        requireMember(event.conversationId, userId)

        val rsvp = GroupEventRsvp(eventId = eventId, userId = userId, status = status)
        return groupEventRepository.saveRsvp(rsvp)
    }

    @Transactional(readOnly = true)
    override fun getRsvps(eventId: UUID): List<GroupEventRsvp> =
        groupEventRepository.findRsvpsByEventId(eventId)

    private fun requireMember(conversationId: UUID, userId: UUID) {
        conversationRepository.findMember(conversationId, userId)
            ?: throw BusinessException(ErrorCode.GROUP_NOT_MEMBER)
    }
}
