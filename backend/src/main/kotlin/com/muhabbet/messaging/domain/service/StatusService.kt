package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Status
import com.muhabbet.messaging.domain.port.`in`.ManageStatusUseCase
import com.muhabbet.messaging.domain.port.`in`.StatusGroup
import com.muhabbet.messaging.domain.port.out.StatusRepository
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class StatusService(
    private val statusRepository: StatusRepository
) : ManageStatusUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun createStatus(userId: UUID, content: String?, mediaUrl: String?): Status {
        val status = Status(
            userId = userId,
            content = content,
            mediaUrl = mediaUrl
        )
        val saved = statusRepository.save(status)
        log.info("Status created: id={}, user={}", saved.id, userId)
        return saved
    }

    @Transactional
    fun createStatusWithAudience(
        userId: UUID,
        content: String?,
        mediaUrl: String?,
        visibility: String,
        excludedUserIds: List<UUID>,
        includedUserIds: List<UUID>
    ): Status {
        val status = Status(
            userId = userId,
            content = content,
            mediaUrl = mediaUrl,
            visibility = visibility,
            excludedUserIds = excludedUserIds,
            includedUserIds = includedUserIds
        )
        val saved = statusRepository.save(status)
        log.info("Status created with audience: id={}, user={}, visibility={}", saved.id, userId, visibility)
        return saved
    }

    @Transactional(readOnly = true)
    override fun getMyStatuses(userId: UUID): List<Status> {
        return statusRepository.findActiveByUserId(userId)
    }

    @Transactional(readOnly = true)
    override fun getContactStatuses(): List<StatusGroup> {
        return statusRepository.findAllActive()
            .groupBy { it.userId }
            .map { (userId, statuses) ->
                StatusGroup(
                    userId = userId,
                    statuses = statuses.sortedByDescending { it.createdAt }
                )
            }
    }

    @Transactional(readOnly = true)
    fun getContactStatusesForUser(viewerUserId: UUID): List<StatusGroup> {
        return statusRepository.findAllActive()
            .filter { status ->
                when (status.visibility) {
                    "everyone" -> viewerUserId !in status.excludedUserIds
                    "contacts_except" -> viewerUserId !in status.excludedUserIds
                    "only_share_with" -> viewerUserId in status.includedUserIds
                    else -> true
                }
            }
            .groupBy { it.userId }
            .map { (userId, statuses) ->
                StatusGroup(
                    userId = userId,
                    statuses = statuses.sortedByDescending { it.createdAt }
                )
            }
    }

    @Transactional
    override fun deleteStatus(statusId: UUID, userId: UUID) {
        val status = statusRepository.findById(statusId)
        if (status != null && status.userId == userId) {
            statusRepository.delete(statusId)
            log.info("Status deleted: id={}, user={}", statusId, userId)
        }
    }
}
