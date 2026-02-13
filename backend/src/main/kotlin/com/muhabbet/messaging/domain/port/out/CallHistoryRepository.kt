package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.service.CallHistoryRecord
import java.util.UUID

interface CallHistoryRepository {
    fun save(record: CallHistoryRecord): CallHistoryRecord
    fun findByUserId(userId: UUID, limit: Int, offset: Int): List<CallHistoryRecord>
    fun countByUserId(userId: UUID): Int
}
