package com.muhabbet.messaging.domain.port.`in`

import java.util.UUID

/** Marks a status as seen by its viewer (#787). */
interface RecordStatusViewUseCase {

    /**
     * Records that [viewerId] has seen [statusId]. Idempotent. Only a viewer entitled to see the
     * status may record a view of it — anyone else gets `STATUS_NOT_FOUND`, the same answer as for
     * a status that does not exist, so the endpoint cannot be used to probe for statuses. A view of
     * one's own status is accepted and not recorded: an author is not their own audience.
     */
    fun recordView(statusId: UUID, viewerId: UUID)
}
