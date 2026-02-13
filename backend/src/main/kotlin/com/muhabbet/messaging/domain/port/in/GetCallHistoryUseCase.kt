package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.service.CallHistoryRecord
import java.util.UUID

interface GetCallHistoryUseCase {
    fun getCallHistory(userId: UUID, page: Int, size: Int): CallHistoryPage
}

data class CallHistoryPage(
    val items: List<CallHistoryRecord>,
    val totalCount: Int,
    val page: Int,
    val size: Int,
    val hasMore: Boolean
)
