package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Status
import java.util.UUID

interface StatusRepository {
    fun save(status: Status): Status
    fun findById(id: UUID): Status?
    fun findActiveByUserId(userId: UUID): List<Status>
    fun findAllActive(): List<Status>
    fun delete(id: UUID)
}
