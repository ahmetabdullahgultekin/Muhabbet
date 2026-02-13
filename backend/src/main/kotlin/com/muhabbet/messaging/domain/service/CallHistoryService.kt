package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.`in`.CallHistoryPage
import com.muhabbet.messaging.domain.port.`in`.GetCallHistoryUseCase
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class CallHistoryService(
    private val callHistoryRepository: CallHistoryRepository
) : GetCallHistoryUseCase {

    @Transactional(readOnly = true)
    override fun getCallHistory(userId: UUID, page: Int, size: Int): CallHistoryPage {
        val effectivePage = page.coerceAtLeast(0)
        val effectiveSize = size.coerceIn(1, 50)
        val offset = effectivePage * effectiveSize

        val items = callHistoryRepository.findByUserId(userId, effectiveSize, offset)
        val totalCount = callHistoryRepository.countByUserId(userId)
        val hasMore = offset + effectiveSize < totalCount

        return CallHistoryPage(
            items = items,
            totalCount = totalCount,
            page = effectivePage,
            size = effectiveSize,
            hasMore = hasMore
        )
    }
}
