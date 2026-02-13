package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.ChannelAnalyticsJpaEntity
import com.muhabbet.messaging.domain.port.out.ChannelAnalyticsRepository
import com.muhabbet.messaging.domain.port.out.ChannelDailyStats
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.UUID

@Component
class ChannelAnalyticsPersistenceAdapter(
    private val springDataChannelAnalyticsRepository: SpringDataChannelAnalyticsRepository,
    private val springDataConversationMemberRepository: com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataConversationMemberRepository
) : ChannelAnalyticsRepository {

    override fun findByChannelIdAndDateRange(
        channelId: UUID, startDate: LocalDate, endDate: LocalDate
    ): List<ChannelDailyStats> {
        return springDataChannelAnalyticsRepository
            .findByChannelIdAndDateBetweenOrderByDateDesc(channelId, startDate, endDate)
            .map { it.toDomain() }
    }

    override fun incrementViewCount(channelId: UUID, date: LocalDate) {
        val entity = springDataChannelAnalyticsRepository
            .findByChannelIdAndDate(channelId, date)
            ?: ChannelAnalyticsJpaEntity(channelId = channelId, date = date)
        entity.viewCount++
        springDataChannelAnalyticsRepository.save(entity)
    }

    override fun incrementMessageCount(channelId: UUID, date: LocalDate) {
        val entity = springDataChannelAnalyticsRepository
            .findByChannelIdAndDate(channelId, date)
            ?: ChannelAnalyticsJpaEntity(channelId = channelId, date = date)
        entity.messageCount++
        springDataChannelAnalyticsRepository.save(entity)
    }

    override fun getSubscriberCount(channelId: UUID): Int {
        return springDataConversationMemberRepository.countByConversationId(channelId).toInt()
    }

    private fun ChannelAnalyticsJpaEntity.toDomain() = ChannelDailyStats(
        channelId = channelId,
        date = date,
        messageCount = messageCount,
        viewCount = viewCount,
        subscriberGained = subscriberGained,
        subscriberLost = subscriberLost,
        reactionCount = reactionCount
    )
}
