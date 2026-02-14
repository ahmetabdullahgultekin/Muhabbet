package com.muhabbet.messaging.adapter.out.persistence.entity

import jakarta.persistence.*
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "channel_analytics")
class ChannelAnalyticsJpaEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "channel_id", nullable = false) val channelId: UUID,
    @Column(nullable = false) val date: LocalDate,
    @Column(name = "message_count", nullable = false) var messageCount: Int = 0,
    @Column(name = "view_count", nullable = false) var viewCount: Int = 0,
    @Column(name = "subscriber_gained", nullable = false) var subscriberGained: Int = 0,
    @Column(name = "subscriber_lost", nullable = false) var subscriberLost: Int = 0,
    @Column(name = "reaction_count", nullable = false) var reactionCount: Int = 0
)
