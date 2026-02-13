package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageReactionUseCase
import com.muhabbet.messaging.domain.port.`in`.ReactionGroup
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.ReactionRequest
import com.muhabbet.shared.dto.ReactionResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/messages")
class ReactionController(
    private val manageReactionUseCase: ManageReactionUseCase
) {

    @PostMapping("/{messageId}/reactions")
    fun addReaction(
        @PathVariable messageId: UUID,
        @RequestBody request: ReactionRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageReactionUseCase.addReaction(messageId, userId, request.emoji)
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{messageId}/reactions/{emoji}")
    fun removeReaction(
        @PathVariable messageId: UUID,
        @PathVariable emoji: String
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        manageReactionUseCase.removeReaction(messageId, userId, emoji)
        return ApiResponseBuilder.ok(Unit)
    }

    @GetMapping("/{messageId}/reactions")
    fun getReactions(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<List<ReactionResponse>>> {
        val reactions = manageReactionUseCase.getReactions(messageId)
        return ApiResponseBuilder.ok(reactions.map { it.toDto() })
    }

    private fun ReactionGroup.toDto() = ReactionResponse(
        emoji = emoji,
        count = count,
        userIds = userIds.map { it.toString() }
    )
}
