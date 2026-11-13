package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.`in`.ChannelAnalyticsData
import com.muhabbet.messaging.domain.port.`in`.ChannelAnalyticsSummary
import com.muhabbet.messaging.domain.port.`in`.ManageChannelAnalyticsUseCase
import com.muhabbet.messaging.domain.port.out.ChannelAnalyticsRepository
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.transaction.TransactionRunner
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.UUID

class ChannelAnalyticsService(
    private val analyticsRepository: ChannelAnalyticsRepository,
    private val conversationRepository: ConversationRepository,
    private val transactions: TransactionRunner
) : ManageChannelAnalyticsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getAnalytics(
        channelId: UUID, userId: UUID, startDate: LocalDate, endDate: LocalDate
    ): ChannelAnalyticsSummary = transactions.inReadOnlyTransaction {
        // Verify user is channel owner/admin
        val member = conversationRepository.findMember(channelId, userId)
            ?: throw BusinessException(ErrorCode.CHANNEL_NOT_FOUND)

        val stats = analyticsRepository.findByChannelIdAndDateRange(channelId, startDate, endDate)
        val totalSubscribers = analyticsRepository.getSubscriberCount(channelId)

        return@inReadOnlyTransaction ChannelAnalyticsSummary(
            channelId = channelId.toString(),
            totalSubscribers = totalSubscribers,
            dailyStats = stats.map { stat ->
                ChannelAnalyticsData(
                    channelId = channelId.toString(),
                    date = stat.date.toString(),
                    messageCount = stat.messageCount,
                    viewCount = stat.viewCount,
                    subscriberGained = stat.subscriberGained,
                    subscriberLost = stat.subscriberLost,
                    reactionCount = stat.reactionCount
                )
            }
        )
    }

    override fun recordView(channelId: UUID, userId: UUID) {
        transactions.inTransaction<Unit> {
            val today = LocalDate.now()
            analyticsRepository.incrementViewCount(channelId, today)
        }
    }
}
