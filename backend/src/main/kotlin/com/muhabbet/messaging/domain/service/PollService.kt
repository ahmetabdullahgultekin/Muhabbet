package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PollVote
import com.muhabbet.messaging.domain.port.`in`.ManagePollUseCase
import com.muhabbet.messaging.domain.port.`in`.PollOptionInfo
import com.muhabbet.messaging.domain.port.`in`.PollResult
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import com.muhabbet.shared.dto.PollData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * A vote changes a count everyone in the conversation sees, and the results say what a group is
 * deciding. Both were reachable with nothing but a message id until #557 — no membership check
 * existed on either path.
 */
open class PollService(
    private val messageRepository: MessageRepository,
    private val pollVoteRepository: PollVoteRepository,
    private val conversationRepository: ConversationRepository
) : ManagePollUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    @Transactional
    override fun vote(messageId: UUID, userId: UUID, optionIndex: Int): PollResult {
        val message = requireMemberOfMessageConversation(messageId, userId)

        val pollData = json.decodeFromString<PollData>(message.content)
        if (optionIndex < 0 || optionIndex >= pollData.options.size) {
            throw BusinessException(ErrorCode.POLL_INVALID_OPTION)
        }

        // Upsert vote (remove old, add new)
        pollVoteRepository.deleteByMessageIdAndUserId(messageId, userId)
        pollVoteRepository.save(
            PollVote(
                messageId = messageId,
                userId = userId,
                optionIndex = optionIndex
            )
        )

        val votes = pollVoteRepository.findByMessageId(messageId)
        log.debug("Poll vote recorded: msg={}, user={}, option={}", messageId, userId, optionIndex)
        return buildResult(messageId, pollData, votes, userId)
    }

    @Transactional(readOnly = true)
    override fun getResults(messageId: UUID, userId: UUID): PollResult {
        val message = requireMemberOfMessageConversation(messageId, userId)

        val pollData = json.decodeFromString<PollData>(message.content)
        val votes = pollVoteRepository.findByMessageId(messageId)
        return buildResult(messageId, pollData, votes, userId)
    }

    /**
     * Runs before the option index is validated, so an outsider cannot use the difference between
     * "invalid option" and "accepted" to count how many options a poll they cannot see has.
     */
    private fun requireMemberOfMessageConversation(messageId: UUID, userId: UUID): Message {
        val message = messageRepository.findById(messageId)
            ?: throw BusinessException(ErrorCode.POLL_MESSAGE_NOT_FOUND)
        conversationRepository.findMember(message.conversationId, userId)
            ?: throw BusinessException(ErrorCode.MSG_NOT_MEMBER)
        return message
    }

    private fun buildResult(
        messageId: UUID,
        pollData: PollData,
        votes: List<PollVote>,
        userId: UUID
    ): PollResult {
        val voteCounts = votes.groupBy { it.optionIndex }.mapValues { it.value.size }
        val myVote = votes.firstOrNull { it.userId == userId }?.optionIndex

        return PollResult(
            messageId = messageId,
            options = pollData.options.mapIndexed { index, text ->
                PollOptionInfo(index = index, text = text, count = voteCounts[index] ?: 0)
            },
            totalVotes = votes.size,
            myVote = myVote
        )
    }
}
