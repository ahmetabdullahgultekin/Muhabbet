package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Status
import java.util.UUID

interface StatusRepository {
    fun save(status: Status): Status
    fun findById(id: UUID): Status?
    fun findActiveByUserId(userId: UUID): List<Status>

    /**
     * Unexpired statuses posted by any of [userIds], batched into one query.
     *
     * There is deliberately no "find every active status" method. The one that existed loaded the
     * whole table and was the direct cause of #507: a viewer with no relationship to anybody was
     * served every status on the instance. Callers must name the audience they are entitled to,
     * so the scope is decided before the query rather than filtered afterwards.
     */
    fun findActiveByUserIds(userIds: Collection<UUID>): List<Status>

    fun delete(id: UUID)
}
