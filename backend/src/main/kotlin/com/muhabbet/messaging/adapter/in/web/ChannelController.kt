package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ChannelInfo
import com.muhabbet.messaging.domain.port.`in`.ManageChannelUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.ChannelInfoResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/channels")
class ChannelController(
    private val manageChannelUseCase: ManageChannelUseCase
) {

    @PostMapping("/{channelId}/subscribe")
    fun subscribe(@PathVariable channelId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageChannelUseCase.subscribe(channelId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{channelId}/subscribe")
    fun unsubscribe(@PathVariable channelId: UUID): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageChannelUseCase.unsubscribe(channelId, userId)
        return ApiResponseBuilder.ok(Unit)
    }

    @GetMapping
    fun listChannels(): ResponseEntity<ApiResponse<List<ChannelInfoResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val channels = manageChannelUseCase.listChannels(userId)
        return ApiResponseBuilder.ok(channels.map { it.toDto() })
    }

    private fun ChannelInfo.toDto() = ChannelInfoResponse(
        id = id.toString(),
        name = name,
        description = description,
        subscriberCount = subscriberCount,
        isSubscribed = isSubscribed,
        createdAt = createdAt
    )
}
