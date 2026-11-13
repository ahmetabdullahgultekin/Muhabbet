package com.muhabbet.moderation.domain.port.out

import com.muhabbet.moderation.domain.model.MessageEvidence
import java.util.UUID

/**
 * What moderation needs from messaging to accept a report: whether the reporter could see what they
 * are reporting, and a copy of it (#891).
 *
 * Declared here, in moderation's own terms, so the domain imports nothing from messaging; the
 * adapter behind it is the one place the two modules meet — the same trade as [UserDirectoryPort].
 */
interface ReportEvidencePort {
    /**
     * A snapshot of [messageId] if [reporterId] is a member of its conversation and it is not
     * deleted, else null. "Not found" and "not yours to see" deliberately look the same, so the
     * report endpoint cannot be used to probe for message ids.
     */
    fun snapshotVisibleMessage(reporterId: UUID, messageId: UUID): MessageEvidence?

    /** Whether [reporterId] is a member of [conversationId]. */
    fun isConversationMember(reporterId: UUID, conversationId: UUID): Boolean
}
