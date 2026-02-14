package com.muhabbet.messaging.domain.port.`in`

import java.time.LocalDate
import java.util.UUID

data class ChannelAnalyticsData(
    val channelId: String,
    val date: String,
    val messageCount: Int,
    val viewCount: Int,
    val subscriberGained: Int,
    val subscriberLost: Int,
    val reactionCount: Int
)

data class ChannelAnalyticsSummary(
    val channelId: String,
    val totalSubscribers: Int,
    val dailyStats: List<ChannelAnalyticsData>
)

interface ManageChannelAnalyticsUseCase {
    fun getAnalytics(channelId: UUID, userId: UUID, startDate: LocalDate, endDate: LocalDate): ChannelAnalyticsSummary
    fun recordView(channelId: UUID, userId: UUID)
}
