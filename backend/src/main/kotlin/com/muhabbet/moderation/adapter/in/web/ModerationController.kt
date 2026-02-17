package com.muhabbet.moderation.adapter.`in`.web

import com.muhabbet.moderation.domain.model.ReportReason
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReportUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReviewReportsUseCase
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/moderation")
class ModerationController(
    private val reportUserUseCase: ReportUserUseCase,
    private val blockUserUseCase: BlockUserUseCase,
    private val reviewReportsUseCase: ReviewReportsUseCase
) {

    // ─── Report ──────────────────────────────────────────

    @PostMapping("/reports")
    fun createReport(
        @RequestBody request: CreateReportRequest
    ): ResponseEntity<*> {
        val currentUserId = AuthenticatedUser.currentUserId()
        val report = reportUserUseCase.reportUser(
            reporterId = currentUserId,
            reportedUserId = request.reportedUserId?.let { UUID.fromString(it) },
            reportedMessageId = request.reportedMessageId?.let { UUID.fromString(it) },
            reportedConversationId = request.reportedConversationId?.let { UUID.fromString(it) },
            reason = ReportReason.valueOf(request.reason),
            description = request.description
        )
        return ApiResponseBuilder.ok(mapOf("reportId" to report.id.toString()))
    }

    // ─── Block ───────────────────────────────────────────

    @PostMapping("/blocks/{userId}")
    fun blockUser(
        @PathVariable userId: String
    ): ResponseEntity<*> {
        val currentUserId = AuthenticatedUser.currentUserId()
        blockUserUseCase.blockUser(currentUserId, UUID.fromString(userId))
        return ApiResponseBuilder.ok(mapOf("blocked" to true))
    }

    @DeleteMapping("/blocks/{userId}")
    fun unblockUser(
        @PathVariable userId: String
    ): ResponseEntity<*> {
        val currentUserId = AuthenticatedUser.currentUserId()
        blockUserUseCase.unblockUser(currentUserId, UUID.fromString(userId))
        return ApiResponseBuilder.ok(mapOf("blocked" to false))
    }

    @GetMapping("/blocks")
    fun getBlockedUsers(): ResponseEntity<*> {
        val currentUserId = AuthenticatedUser.currentUserId()
        val blockedIds = blockUserUseCase.getBlockedUsers(currentUserId)
        return ApiResponseBuilder.ok(mapOf("blockedUserIds" to blockedIds.map { it.toString() }))
    }

    @GetMapping("/blocks/{userId}")
    fun checkBlocked(
        @PathVariable userId: String
    ): ResponseEntity<*> {
        val currentUserId = AuthenticatedUser.currentUserId()
        val blocked = blockUserUseCase.isBlocked(currentUserId, UUID.fromString(userId))
        return ApiResponseBuilder.ok(mapOf("blocked" to blocked))
    }

    // ─── Admin: Review Reports ───────────────────────────

    @GetMapping("/reports/pending")
    fun getPendingReports(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<*> {
        val reports = reviewReportsUseCase.getPendingReports(limit, offset)
        return ApiResponseBuilder.ok(reports)
    }

    @PostMapping("/reports/{reportId}/resolve")
    fun resolveReport(
        @PathVariable reportId: String,
        @RequestParam(defaultValue = "false") dismiss: Boolean
    ): ResponseEntity<*> {
        val currentUserId = AuthenticatedUser.currentUserId()
        reviewReportsUseCase.resolveReport(UUID.fromString(reportId), currentUserId, dismiss)
        return ApiResponseBuilder.ok(mapOf("resolved" to true))
    }
}

data class CreateReportRequest(
    val reportedUserId: String? = null,
    val reportedMessageId: String? = null,
    val reportedConversationId: String? = null,
    val reason: String,
    val description: String? = null
)
