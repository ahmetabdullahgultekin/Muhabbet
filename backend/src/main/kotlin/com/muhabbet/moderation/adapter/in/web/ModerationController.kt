package com.muhabbet.moderation.adapter.`in`.web

import com.muhabbet.moderation.domain.model.ReportReason
import com.muhabbet.moderation.domain.port.`in`.BlockUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReportUserUseCase
import com.muhabbet.moderation.domain.port.`in`.ReviewReportsUseCase
import com.muhabbet.moderation.domain.port.out.UserDirectoryPort
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.BlockedUserResponse
import com.muhabbet.shared.dto.CreateReportRequest
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
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
    private val reviewReportsUseCase: ReviewReportsUseCase,
    private val userDirectoryPort: UserDirectoryPort
) {

    // ─── Report ──────────────────────────────────────────

    @PostMapping("/reports")
    fun createReport(
        @RequestBody request: CreateReportRequest
    ): ResponseEntity<*> {
        val currentUserId = AuthenticatedUser.currentUserId()
        val reason = try {
            ReportReason.valueOf(request.reason)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }
        val reportedUserId = request.reportedUserId?.let {
            try { UUID.fromString(it) } catch (_: IllegalArgumentException) {
                throw BusinessException(ErrorCode.VALIDATION_ERROR)
            }
        }
        val reportedMessageId = request.reportedMessageId?.let {
            try { UUID.fromString(it) } catch (_: IllegalArgumentException) {
                throw BusinessException(ErrorCode.VALIDATION_ERROR)
            }
        }
        val reportedConversationId = request.reportedConversationId?.let {
            try { UUID.fromString(it) } catch (_: IllegalArgumentException) {
                throw BusinessException(ErrorCode.VALIDATION_ERROR)
            }
        }
        val report = reportUserUseCase.reportUser(
            reporterId = currentUserId,
            reportedUserId = reportedUserId,
            reportedMessageId = reportedMessageId,
            reportedConversationId = reportedConversationId,
            reason = reason,
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
        val targetId = try { UUID.fromString(userId) } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }
        blockUserUseCase.blockUser(currentUserId, targetId)
        return ApiResponseBuilder.ok(mapOf("blocked" to true))
    }

    @DeleteMapping("/blocks/{userId}")
    fun unblockUser(
        @PathVariable userId: String
    ): ResponseEntity<*> {
        val currentUserId = AuthenticatedUser.currentUserId()
        val targetId = try { UUID.fromString(userId) } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }
        blockUserUseCase.unblockUser(currentUserId, targetId)
        return ApiResponseBuilder.ok(mapOf("blocked" to false))
    }

    /**
     * The caller's own block list, with enough to actually render a row: a name and a face, not
     * just a UUID. Was `{ "blockedUserIds": [...] }` — bare ids nothing on the client could turn
     * into a name, since `GET /users/{id}` withholds a foreign user's phone number and there is no
     * local contact-book entry for someone the user has never messaged from this device. Resolving
     * [displayName]/[avatarUrl] here, in one batched call via [userDirectoryPort], is strictly
     * better than pushing that N-request problem onto every client that ever wants this list.
     *
     * Newest block first: the person you just blocked is the one most likely to be why this screen
     * was opened.
     */
    @GetMapping("/blocks")
    fun getBlockedUsers(): ResponseEntity<ApiResponse<List<BlockedUserResponse>>> {
        val currentUserId = AuthenticatedUser.currentUserId()
        val blocks = blockUserUseCase.getBlockedUsers(currentUserId)
            .sortedByDescending { it.createdAt }
        val displayInfo = userDirectoryPort.findDisplayInfo(blocks.map { it.blockedId })
        val response = blocks.map { block ->
            val info = displayInfo[block.blockedId]
            BlockedUserResponse(
                userId = block.blockedId.toString(),
                displayName = info?.displayName,
                avatarUrl = info?.avatarUrl,
                blockedAt = block.createdAt.toString()
            )
        }
        return ApiResponseBuilder.ok(response)
    }

    @GetMapping("/blocks/{userId}")
    fun checkBlocked(
        @PathVariable userId: String
    ): ResponseEntity<*> {
        val currentUserId = AuthenticatedUser.currentUserId()
        val targetId = try { UUID.fromString(userId) } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }
        val blocked = blockUserUseCase.isBlocked(currentUserId, targetId)
        return ApiResponseBuilder.ok(mapOf("blocked" to blocked))
    }

    // ─── Admin: Review Reports ───────────────────────────

    @GetMapping("/reports/pending")
    fun getPendingReports(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<*> {
        AuthenticatedUser.requireAdmin()
        val reports = reviewReportsUseCase.getPendingReports(limit, offset)
        return ApiResponseBuilder.ok(reports)
    }

    @PostMapping("/reports/{reportId}/resolve")
    fun resolveReport(
        @PathVariable reportId: String,
        @RequestParam(defaultValue = "false") dismiss: Boolean
    ): ResponseEntity<*> {
        AuthenticatedUser.requireAdmin()
        val currentUserId = AuthenticatedUser.currentUserId()
        val reportUUID = try { UUID.fromString(reportId) } catch (_: IllegalArgumentException) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }
        reviewReportsUseCase.resolveReport(reportUUID, currentUserId, dismiss)
        return ApiResponseBuilder.ok(mapOf("resolved" to true))
    }
}
