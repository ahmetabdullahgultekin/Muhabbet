package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageChannelAnalyticsUseCase
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable channelId: String,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<*> {
        val start = startDate?.let { LocalDate.parse(it) } ?: LocalDate.now().minusDays(30)
        val end = endDate?.let { LocalDate.parse(it) } ?: LocalDate.now()
        val analytics = channelAnalyticsUseCase.getAnalytics(UUID.fromString(channelId), user.userId, start, end)
        return ApiResponseBuilder.ok(analytics)
    }

    @PostMapping("/view")
    fun recordView(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @PathVariable channelId: String
    ): ResponseEntity<*> {
        channelAnalyticsUseCase.recordView(UUID.fromString(channelId), user.userId)
        return ApiResponseBuilder.ok(mapOf("recorded" to true))
    }
}
