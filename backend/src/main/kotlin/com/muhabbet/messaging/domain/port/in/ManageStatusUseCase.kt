package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Status
import java.util.UUID

interface ManageStatusUseCase {
    fun createStatus(userId: UUID, content: String?, mediaUrl: String?): Status
    fun getMyStatuses(userId: UUID): List<Status>
    fun getContactStatuses(): List<StatusGroup>
    fun deleteStatus(statusId: UUID, userId: UUID)
}

data class StatusGroup(
    val userId: UUID,
    val statuses: List<Status>
)
