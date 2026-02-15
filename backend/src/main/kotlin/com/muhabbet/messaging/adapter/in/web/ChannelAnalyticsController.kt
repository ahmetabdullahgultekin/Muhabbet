package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageChannelAnalyticsUseCase
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/channels/{channelId}/analytics")
class ChannelAnalyticsController(
    private val channelAnalyticsUseCase: ManageChannelAnalyticsUseCase
) {

    @GetMapping
    fun getAnalytics(
        @PathVariable channelId: String,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        val start = startDate?.let { LocalDate.parse(it) } ?: LocalDate.now().minusDays(30)
        val end = endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val analytics = channelAnalyticsUseCase.getAnalytics(UUID.fromString(channelId), userId, start, end)
        return ApiResponseBuilder.ok(analytics)
    }

    @PostMapping("/view")
    fun recordView(
        @PathVariable channelId: String
    ): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        channelAnalyticsUseCase.recordView(UUID.fromString(channelId), userId)
        return ApiResponseBuilder.ok(mapOf("recorded" to true))
    }
}
