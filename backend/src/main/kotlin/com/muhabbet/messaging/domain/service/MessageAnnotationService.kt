package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.ContentType
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.model.PollVote
import com.muhabbet.messaging.domain.port.`in`.MessageAnnotations
import com.muhabbet.messaging.domain.port.`in`.PollCounts
import com.muhabbet.messaging.domain.port.`in`.ResolveMessageAnnotationsUseCase
import com.muhabbet.messaging.domain.port.out.PollVoteRepository
import com.muhabbet.messaging.domain.port.out.ReactionRepository
import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
import com.muhabbet.shared.dto.PollData
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * At most three queries per page, whatever its size: the viewer's stars, every reaction, and every
 * poll vote — the last only when the page holds a poll. See [ResolveMessageAnnotationsUseCase].
 *
 * Kept out of `MessageService` on purpose: that class is the send/receive hot path and already
 * implements its quota of use cases, and none of this has anything to do with delivering a message.
 */
open class MessageAnnotationService(
    private val starredMessageRepository: StarredMessageRepository,
    private val reactionRepository: ReactionRepository,
    private val pollVoteRepository: PollVoteRepository
) : ResolveMessageAnnotationsUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    @Transactional(readOnly = true)
    override fun resolveAnnotations(messages: List<Message>, viewerId: UUID): Map<UUID, MessageAnnotations> {
        if (messages.isEmpty()) return emptyMap()
        val ids = messages.map { it.id }

        val starred = starredMessageRepository.getStarredMessageIds(viewerId, ids)
        val reactionsByMessage = reactionRepository.findByMessageIds(ids).groupBy { it.messageId }

        // A deleted poll shows no tally: its question and options are gone from the body, so a set
        // of bars would be counts of nothing.
        val polls = messages.filter { it.contentType == ContentType.POLL && !it.isDeleted }
        val pollIds = polls.mapTo(HashSet()) { it.id }
        val votesByPoll = if (polls.isEmpty()) {
            emptyMap()
        } else {
            pollVoteRepository.findByMessageIds(pollIds).groupBy { it.messageId }
        }

        return messages.associate { message ->
            val reactions = reactionsByMessage[message.id].orEmpty()
            message.id to MessageAnnotations(
                isStarred = message.id in starred,
                reactions = reactions.groupingBy { it.emoji }.eachCount(),
                myReactions = reactions.filter { it.userId == viewerId }.map { it.emoji }.toSet(),
                poll = if (message.id in pollIds) {
                    pollCountsOf(message, votesByPoll[message.id].orEmpty(), viewerId)
                } else {
                    null
                }
            )
        }
    }

    private fun pollCountsOf(
        message: Message,
        votes: List<PollVote>,
        viewerId: UUID
    ): PollCounts? {
        val options = try {
            json.decodeFromString<PollData>(message.content).options
        } catch (e: IllegalArgumentException) {
            // A body that is not a poll yields no tally rather than failing the whole page: one bad
            // row must not cost the user the rest of their history.
            log.warn("Poll message {} has an unreadable body: {}", message.id, e.message)
            return null
        }
        return tallyPoll(options.size, votes, viewerId)
    }
}
