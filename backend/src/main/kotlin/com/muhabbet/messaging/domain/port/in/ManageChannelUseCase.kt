package com.muhabbet.messaging.domain.port.`in`

import java.util.UUID

interface ManageChannelUseCase {
    fun subscribe(channelId: UUID, userId: UUID)
    fun unsubscribe(channelId: UUID, userId: UUID)
    fun listChannels(userId: UUID): List<ChannelInfo>
}

data class ChannelInfo(
    val id: UUID,
    val name: String,
    val description: String?,
    val subscriberCount: Int,
    val isSubscribed: Boolean,
    val createdAt: String
)
