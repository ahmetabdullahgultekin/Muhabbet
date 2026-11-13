package com.muhabbet.messaging.domain.port.out

import java.time.Instant
import java.util.UUID

/** Who has seen which status (#787). */
interface StatusViewRepository {

    /** Records that [viewerId] saw [statusId]. Idempotent: a repeat view changes nothing. */
    fun recordView(statusId: UUID, viewerId: UUID, viewedAt: Instant)

    /** The subset of [statusIds] that [viewerId] has seen, in one query. */
    fun findViewedStatusIds(viewerId: UUID, statusIds: Collection<UUID>): Set<UUID>
}
