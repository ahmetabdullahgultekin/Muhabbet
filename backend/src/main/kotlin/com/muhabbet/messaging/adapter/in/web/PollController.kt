package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManagePollUseCase
import com.muhabbet.messaging.domain.port.`in`.PollResult
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.PollOptionResult
import com.muhabbet.shared.dto.PollResultResponse
import com.muhabbet.shared.dto.PollVoteRequest
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/polls")
class PollController(
    private val managePollUseCase: ManagePollUseCase
) {

    @PostMapping("/{messageId}/vote")
    fun vote(
        @PathVariable messageId: UUID,
        @RequestBody request: PollVoteRequest
    ): ResponseEntity<ApiResponse<PollResultResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val result = managePollUseCase.vote(messageId, userId, request.optionIndex)
        return ApiResponseBuilder.ok(result.toDto())
    }

    @GetMapping("/{messageId}/results")
    fun getResults(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<PollResultResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val result = managePollUseCase.getResults(messageId, userId)
        return ApiResponseBuilder.ok(result.toDto())
    }

    private fun PollResult.toDto() = PollResultResponse(
        messageId = messageId.toString(),
        votes = options.map { PollOptionResult(index = it.index, text = it.text, count = it.count) },
        totalVotes = totalVotes,
        myVote = myVote
    )
}
