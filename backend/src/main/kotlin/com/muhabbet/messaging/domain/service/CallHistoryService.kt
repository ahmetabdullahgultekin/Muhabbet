package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.`in`.CallHistoryPage
import com.muhabbet.messaging.domain.port.`in`.GetCallHistoryUseCase
import com.muhabbet.messaging.domain.port.out.CallHistoryRepository
import com.muhabbet.shared.transaction.TransactionRunner
import java.util.UUID

class CallHistoryService(
    private val callHistoryRepository: CallHistoryRepository,
    private val transactions: TransactionRunner
) : GetCallHistoryUseCase {

    override fun getCallHistory(userId: UUID, page: Int, size: Int): CallHistoryPage = transactions.inReadOnlyTransaction {
        val effectivePage = page.coerceAtLeast(0)
        val effectiveSize = size.coerceIn(1, 50)
        val offset = effectivePage * effectiveSize

        val items = callHistoryRepository.findByUserId(userId, effectiveSize, offset)
        val totalCount = callHistoryRepository.countByUserId(userId)
        val hasMore = offset + effectiveSize < totalCount

        return@inReadOnlyTransaction CallHistoryPage(
            items = items,
            totalCount = totalCount,
            page = effectivePage,
            size = effectiveSize,
            hasMore = hasMore
        )
    }
}
