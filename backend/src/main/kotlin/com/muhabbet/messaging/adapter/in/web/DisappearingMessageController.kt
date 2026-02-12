package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataConversationRepository
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class SetDisappearTimerRequest(val seconds: Int?)

@RestController
@RequestMapping("/api/v1/conversations")
class DisappearingMessageController(
    private val conversationRepo: SpringDataConversationRepository
) {

    @PutMapping("/{conversationId}/disappear")
    fun setDisappearTimer(
        @PathVariable conversationId: UUID,
        @RequestBody request: SetDisappearTimerRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        AuthenticatedUser.currentUserId()
        val conv = conversationRepo.findById(conversationId).orElse(null)
            ?: return ApiResponseBuilder.ok(Unit)

        conv.disappearAfterSeconds = request.seconds
        conversationRepo.save(conv)

        return ApiResponseBuilder.ok(Unit)
    }
}
