package com.muhabbet.messaging.domain.port.out

import java.time.LocalDate
import java.util.UUID

data class ChannelDailyStats(
    val channelId: UUID,
    val date: LocalDate,
    val messageCount: Int,
    val viewCount: Int,
    val subscriberGained: Int,
    val subscriberLost: Int,
    val reactionCount: Int
)

interface ChannelAnalyticsRepository {
    fun findByChannelIdAndDateRange(channelId: UUID, startDate: LocalDate, endDate: LocalDate): List<ChannelDailyStats>
    fun incrementViewCount(channelId: UUID, date: LocalDate)
    fun incrementMessageCount(channelId: UUID, date: LocalDate)
    fun getSubscriberCount(channelId: UUID): Int
}
