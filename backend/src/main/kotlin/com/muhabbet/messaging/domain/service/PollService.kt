package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PollVote
import com.muhabbet.messaging.domain.port.`in`.ManagePollUseCase
import com.muhabbet.messaging.domain.port.`in`.PollOptionInfo
import com.muhabbet.messaging.domain.port.`in`.PollResult
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import com.muhabbet.messaging.domain.port.out.MessageBroadcaster
import com.muhabbet.messaging.domain.port.out.MessageRepository
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import com.muhabbet.messaging.domain.port.out.TransactionRunner
import com.muhabbet.shared.dto.PollData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * A vote changes a count everyone in the conversation sees, and the results say what a group is
 * deciding. Both were reachable with nothing but a message id until #557 — no membership check
 * existed on either path.
 *
 * ### Why [vote] has no `@Transactional` (#781)
 *
 * A vote now fans out a `poll.updated` frame to every member, so other devices see the bars move
 * instead of a frozen poll. Same rule as every other fan-out in this module: the write runs inside
 * [TransactionRunner.inTransaction] and the broadcast happens after it has committed, so no member
 * is told about a vote a rollback then removed, and no pooled connection is held across the socket
 * writes. A method-level `@Transactional` here would absorb the inner one and undo both.
 */
open class PollService(
    private val messageRepository: MessageRepository,
    private val pollVoteRepository: PollVoteRepository,
    private val conversationRepository: ConversationRepository,
    private val messageBroadcaster: MessageBroadcaster,
    private val transactions: TransactionRunner
) : ManagePollUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    private class VoteOutcome(
        val message: Message,
        val pollData: PollData,
        val votes: List<PollVote>,
        val recipients: List<UUID>
    )

    override fun vote(messageId: UUID, userId: UUID, optionIndex: Int): PollResult {
        val outcome = transactions.inTransaction {
            val message = requireMemberOfMessageConversation(messageId, userId)

            val pollData = json.decodeFromString<PollData>(message.content)
            if (optionIndex < 0 || optionIndex >= pollData.options.size) {
                throw BusinessException(ErrorCode.POLL_INVALID_OPTION)
            }

            pollVoteRepository.upsert(
                PollVote(
                    messageId = messageId,
                    userId = userId,
                    optionIndex = optionIndex
                )
            )

            VoteOutcome(
                message = message,
                pollData = pollData,
                votes = pollVoteRepository.findByMessageId(messageId),
                recipients = conversationRepository.findMembersByConversationId(message.conversationId)
                    .map { it.userId }
            )
        }

        val tally = tallyPoll(outcome.pollData.options.size, outcome.votes, viewerId = null)
        messageBroadcaster.broadcastToUsers(
            outcome.recipients,
            WsMessage.PollUpdated(
                messageId = messageId.toString(),
                conversationId = outcome.message.conversationId.toString(),
                voterId = userId.toString(),
                optionIndex = optionIndex,
                counts = tally.counts,
                totalVotes = tally.totalVotes
            )
        )

        log.debug("Poll vote recorded: msg={}, user={}, option={}", messageId, userId, optionIndex)
        return buildResult(messageId, outcome.pollData, outcome.votes, userId)
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
        val tally = tallyPoll(pollData.options.size, votes, userId)
        return PollResult(
            messageId = messageId,
            options = pollData.options.mapIndexed { index, text ->
                PollOptionInfo(index = index, text = text, count = tally.counts[index])
            },
            totalVotes = tally.totalVotes,
            myVote = tally.myVote
        )
    }
}
