package com.muhabbet.app.data.repository

import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.shared.dto.ChannelInfoResponse

class ChannelRepository(private val apiClient: ApiClient) {

    suspend fun listChannels(): List<ChannelInfoResponse> {
        val response = apiClient.get<List<ChannelInfoResponse>>("/api/v1/channels")
        return response.data ?: emptyList()
    }

    suspend fun subscribe(channelId: String) {
        apiClient.post<Unit>("/api/v1/channels/$channelId/subscribe", Unit)
    }

    suspend fun unsubscribe(channelId: String) {
        apiClient.delete("/api/v1/channels/$channelId/subscribe")
    }
}
