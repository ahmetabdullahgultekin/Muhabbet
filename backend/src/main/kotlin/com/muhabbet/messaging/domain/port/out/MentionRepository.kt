package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Mention
import java.util.UUID

/**
 * Out-port for persisting/reading structured @mention rows (`message_mentions` table).
 * Tier-2 group @mentions — see `docs/design/T2-group-mentions.md`, ADR-0008.
 *
 * Only exercised when `muhabbet.mentions.enabled` is true; with the flag OFF the service never
 * calls this port, so the send/history path is byte-identical to pre-mentions behaviour.
 */
interface MentionRepository {
    /** Persist all mention rows for a single message (no-op for an empty list). */
    fun saveAll(messageId: UUID, mentions: List<Mention>)

    /** Mentions for one message, ordered by [Mention.startOffset]. */
    fun findByMessageId(messageId: UUID): List<Mention>

    /** Mentions for several messages at once (batch read for history hydration), keyed by messageId. */
    fun findByMessageIds(messageIds: List<UUID>): Map<UUID, List<Mention>>
}
