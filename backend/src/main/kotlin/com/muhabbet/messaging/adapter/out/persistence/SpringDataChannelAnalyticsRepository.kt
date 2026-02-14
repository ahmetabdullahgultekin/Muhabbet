package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.ChannelAnalyticsJpaEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface SpringDataChannelAnalyticsRepository : JpaRepository<ChannelAnalyticsJpaEntity, UUID> {
    fun findByChannelIdAndDateBetweenOrderByDateDesc(
        channelId: UUID, startDate: LocalDate, endDate: LocalDate
    ): List<ChannelAnalyticsJpaEntity>

    fun findByChannelIdAndDate(channelId: UUID, date: LocalDate): ChannelAnalyticsJpaEntity?
}
