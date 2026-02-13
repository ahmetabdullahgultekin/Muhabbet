package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.adapter.out.persistence.entity.MessageReactionJpaEntity
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataReactionRepository
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.ReactionRequest
import com.muhabbet.shared.dto.ReactionResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
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
    private val reactionRepo: SpringDataReactionRepository
) {

    @PostMapping("/{messageId}/reactions")
    @Transactional
    fun addReaction(
        @PathVariable messageId: UUID,
        @RequestBody request: ReactionRequest
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        val existing = reactionRepo.findByMessageIdAndUserIdAndEmoji(messageId, userId, request.emoji)
        if (existing != null) {
            return ApiResponseBuilder.ok(Unit)
        }
        reactionRepo.save(
            MessageReactionJpaEntity(
                messageId = messageId,
                userId = userId,
                emoji = request.emoji
            )
        )
        return ApiResponseBuilder.ok(Unit)
    }

    @DeleteMapping("/{messageId}/reactions/{emoji}")
    @Transactional
    fun removeReaction(
        @PathVariable messageId: UUID,
        @PathVariable emoji: String
    ): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        reactionRepo.deleteByMessageIdAndUserIdAndEmoji(messageId, userId, emoji)
        return ApiResponseBuilder.ok(Unit)
    }

    @GetMapping("/{messageId}/reactions")
    fun getReactions(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<List<ReactionResponse>>> {
        val reactions = reactionRepo.findByMessageId(messageId)
        val grouped = reactions.groupBy { it.emoji }
            .map { (emoji, list) ->
                ReactionResponse(
                    emoji = emoji,
                    count = list.size,
                    userIds = list.map { it.userId.toString() }
                )
            }
        return ApiResponseBuilder.ok(grouped)
    }
}
