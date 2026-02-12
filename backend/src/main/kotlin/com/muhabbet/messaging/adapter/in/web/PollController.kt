package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataMessageRepository
import com.muhabbet.messaging.adapter.out.persistence.repository.SpringDataPollVoteRepository
import com.muhabbet.messaging.adapter.out.persistence.entity.PollVoteJpaEntity
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.PollData
import com.muhabbet.shared.dto.PollOptionResult
import com.muhabbet.shared.dto.PollResultResponse
import com.muhabbet.shared.dto.PollVoteRequest
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import kotlinx.serialization.json.Json
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
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
    private val messageRepo: SpringDataMessageRepository,
    private val pollVoteRepo: SpringDataPollVoteRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    @PostMapping("/{messageId}/vote")
    @Transactional
    fun vote(
        @PathVariable messageId: UUID,
        @RequestBody request: PollVoteRequest
    ): ResponseEntity<ApiResponse<PollResultResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val message = messageRepo.findById(messageId).orElse(null)
            ?: return ApiResponseBuilder.ok(buildResult(messageId, emptyList(), userId))

        val pollData = json.decodeFromString<PollData>(message.content)
        if (request.optionIndex < 0 || request.optionIndex >= pollData.options.size) {
            return ApiResponseBuilder.ok(buildResult(messageId, emptyList(), userId))
        }

        // Upsert vote (remove old, add new)
        pollVoteRepo.deleteByMessageIdAndUserId(messageId, userId)
        pollVoteRepo.save(
            PollVoteJpaEntity(
                messageId = messageId,
                userId = userId,
                optionIndex = request.optionIndex
            )
        )

        val votes = pollVoteRepo.findByMessageId(messageId)
        return ApiResponseBuilder.ok(buildResult(messageId, pollData, votes, userId))
    }

    @GetMapping("/{messageId}/results")
    fun getResults(@PathVariable messageId: UUID): ResponseEntity<ApiResponse<PollResultResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val message = messageRepo.findById(messageId).orElse(null)
            ?: return ApiResponseBuilder.ok(buildResult(messageId, emptyList(), userId))

        val pollData = json.decodeFromString<PollData>(message.content)
        val votes = pollVoteRepo.findByMessageId(messageId)
        return ApiResponseBuilder.ok(buildResult(messageId, pollData, votes, userId))
    }

    private fun buildResult(
        messageId: UUID,
        pollData: PollData,
        votes: List<PollVoteJpaEntity>,
        userId: UUID
    ): PollResultResponse {
        val voteCounts = votes.groupBy { it.optionIndex }.mapValues { it.value.size }
        val myVote = votes.firstOrNull { it.userId == userId }?.optionIndex

        return PollResultResponse(
            messageId = messageId.toString(),
            votes = pollData.options.mapIndexed { index, text ->
                PollOptionResult(index = index, text = text, count = voteCounts[index] ?: 0)
            },
            totalVotes = votes.size,
            myVote = myVote
        )
    }

    private fun buildResult(
        messageId: UUID,
        votes: List<PollVoteJpaEntity>,
        userId: UUID
    ): PollResultResponse {
        return PollResultResponse(
            messageId = messageId.toString(),
            votes = emptyList(),
            totalVotes = 0,
            myVote = null
        )
    }
}
