package com.muhabbet.messaging.domain.port.`in`

import java.util.UUID

interface ManagePollUseCase {
    fun vote(messageId: UUID, userId: UUID, optionIndex: Int): PollResult
    fun getResults(messageId: UUID, userId: UUID): PollResult
}

data class PollResult(
    val messageId: UUID,
    val options: List<PollOptionInfo>,
    val totalVotes: Int,
    val myVote: Int?
)

data class PollOptionInfo(
    val index: Int,
    val text: String,
    val count: Int
)
