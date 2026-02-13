package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.port.`in`.GetCallHistoryUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.CallHistoryResponse
import com.muhabbet.shared.dto.PaginatedResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/calls")
class CallHistoryController(
    private val getCallHistoryUseCase: GetCallHistoryUseCase,
    private val userRepository: UserRepository
) {

    @GetMapping("/history")
    fun getCallHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<PaginatedResponse<CallHistoryResponse>>> {
        val userId = AuthenticatedUser.currentUserId()
        val result = getCallHistoryUseCase.getCallHistory(userId, page, size)

        // Gather all user IDs for name resolution
        val allUserIds = result.items.flatMap { listOf(it.callerId, it.calleeId) }.distinct()
        val usersMap = userRepository.findAllByIds(allUserIds).associateBy { it.id }

        val items = result.items.map { record ->
            CallHistoryResponse(
                id = record.id.toString(),
                callId = record.callId,
                callerId = record.callerId.toString(),
                calleeId = record.calleeId.toString(),
                callerName = usersMap[record.callerId]?.displayName,
                calleeName = usersMap[record.calleeId]?.displayName,
                callType = record.callType,
                status = record.status,
                startedAt = record.startedAt.toString(),
                answeredAt = record.answeredAt?.toString(),
                endedAt = record.endedAt?.toString(),
                durationSeconds = record.durationSeconds
            )
        }

        val response = PaginatedResponse(
            items = items,
            nextCursor = if (result.hasMore) (result.page + 1).toString() else null,
            hasMore = result.hasMore
        )

        return ApiResponseBuilder.ok(response)
    }
}
