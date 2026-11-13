package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Message
import java.util.UUID

/**
 * Resolves what a viewer sees *around* each message on a page — their star, everyone's reactions
 * and which of them are theirs, and a poll's tally — in a fixed number of queries per page rather
 * than one request per bubble (#775, #781, #783).
 *
 * Every one of these used to be either absent from the history payload or fetched separately: the
 * star was never loaded at all, reactions vanished on every reload, and each poll bubble fetched its
 * own results every time it scrolled back into view.
 *
 * The caller must already have authorised [viewerId] to read [messages] — this is an enrichment of
 * a page the caller was allowed to load, not an access check. Every caller today is a history read
 * that has passed the conversation membership check.
 */
interface ResolveMessageAnnotationsUseCase {
    fun resolveAnnotations(messages: List<Message>, viewerId: UUID): Map<UUID, MessageAnnotations>
}

data class MessageAnnotations(
    val isStarred: Boolean,
    val reactions: Map<String, Int>,
    val myReactions: Set<String>,
    val poll: PollCounts?
) {
    companion object {
        val NONE = MessageAnnotations(
            isStarred = false,
            reactions = emptyMap(),
            myReactions = emptySet(),
            poll = null
        )
    }
}

/**
 * A poll's tally as one viewer sees it. [counts] is indexed by option, [myVote] is the viewer's own
 * option index or null.
 */
data class PollCounts(
    val counts: List<Int>,
    val totalVotes: Int,
    val myVote: Int?
)
