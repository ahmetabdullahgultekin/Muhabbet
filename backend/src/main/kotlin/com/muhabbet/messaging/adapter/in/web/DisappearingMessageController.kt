package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageDisappearingMessageUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import kotlinx.serialization.Serializable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Serializable
data class SetDisappearTimerRequest(val seconds: Int?)

@RestController
@RequestMapping("/api/v1/conversations")
class DisappearingMessageController(
    private val manageDisappearingMessageUseCase: ManageDisappearingMessageUseCase
) {

    @PutMapping("/{conversationId}/disappear")
    fun setDisappearTimer(
        @PathVariable conversationId: UUID,
        @RequestBody request: SetDisappearTimerRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageDisappearingMessageUseCase.setDisappearTimer(conversationId, userId, request.seconds)
        return ApiResponseBuilder.ok(Unit)
    }
}
