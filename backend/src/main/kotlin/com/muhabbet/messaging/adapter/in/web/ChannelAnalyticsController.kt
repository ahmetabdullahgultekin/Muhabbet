package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageChannelAnalyticsUseCase
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/channels/{channelId}/analytics")
class ChannelAnalyticsController(
    private val channelAnalyticsUseCase: ManageChannelAnalyticsUseCase
) {

    /**
     * The dates and the id are **bound**, not parsed here (#401).
     *
     * They used to arrive as `String` and be handed to `LocalDate.parse`, whose
     * `DateTimeParseException` extends `DateTimeException` rather than `IllegalArgumentException`
     * — so it slipped past every client-error arm of `GlobalExceptionHandler` and was answered as
     * 500 `INTERNAL_ERROR` with an ERROR-level stack trace. `?startDate=yarın` told the caller the
     * server had broken and wrote a stack trace into the log that real faults have to compete with.
     * A malformed `channelId` on the very same request was already a 400, by way of
     * `UUID.fromString`; the difference was an accident of exception hierarchies.
     *
     * Letting Spring convert fixes it at the source rather than by widening the handler's net: a
     * value that will not convert raises `MethodArgumentTypeMismatchException`, which carries the
     * parameter's name and its required type, so the 400 can say *which* value was wrong. It also
     * keeps the distinction the handler needs — a `DateTimeParseException` that still escapes from
     * somewhere is now genuinely a server-side parse of server-side data, and stays a 500.
     *
     * `@DateTimeFormat` pins the wire format to ISO explicitly. Spring Boot's `WebConversionService`
     * already defaults to ISO, so this changes nothing today; it says so out loud, so that setting
     * `spring.mvc.format.date` for some other endpoint cannot silently redefine this one's contract.
     */
    @GetMapping
    fun getAnalytics(
        @PathVariable channelId: UUID,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        startDate: LocalDate?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        endDate: LocalDate?
    ): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        // Each default is independent of the other, exactly as before. Deriving `start` from `end`
        // would read better and is not this change's business: #401 is about the status code a
        // malformed value gets, and a silent shift in what an unbounded request means is not
        // something a caller could see coming from that.
        val start = startDate ?: LocalDate.now().minusDays(DEFAULT_WINDOW_DAYS)
        val end = endDate ?: LocalDate.now()
        val analytics = channelAnalyticsUseCase.getAnalytics(channelId, userId, start, end)
        return ApiResponseBuilder.ok(analytics)
    }

    @PostMapping("/view")
    fun recordView(
        @PathVariable channelId: UUID
    ): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        channelAnalyticsUseCase.recordView(channelId, userId)
        return ApiResponseBuilder.ok(mapOf("recorded" to true))
    }

    companion object {
        /** How far back an unbounded request looks. */
        private const val DEFAULT_WINDOW_DAYS = 30L
    }
}
