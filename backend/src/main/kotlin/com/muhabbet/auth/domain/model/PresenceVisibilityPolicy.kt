package com.muhabbet.auth.domain.model

import java.util.UUID

/**
 * Single source of truth for the `onlineStatusVisibility` privacy rule (KVKK data-minimization).
 *
 * The same policy gates BOTH access paths so presence cannot leak on one while being hidden on the
 * other:
 *  - REST pull (`UserController.resolvePresenceVisibility`): is a given requester entitled to see the
 *    target's online state + last-seen?
 *  - Realtime push (`ChatWebSocketHandler` presence/typing broadcast): which recipients may receive
 *    the broadcasting user's online/offline/last-seen/typing events?
 *
 * Semantics (identical to the REST gate):
 *  - "everyone": visible to any user
 *  - "contacts": visible only to users who share a conversation with the subject
 *  - "nobody":   hidden from everyone except the subject themselves
 *  - unknown value: fail closed (hidden) — same as the REST `else -> false` branch
 *
 * The subject always sees their own presence (`subjectId == requesterId`).
 */
object PresenceVisibilityPolicy {

    /**
     * @param visibility the subject's `onlineStatusVisibility` setting
     * @param subjectId the user whose presence is being disclosed
     * @param requesterId the user who would observe the presence
     * @param contactIds the subject's contacts (users sharing a conversation). Pass the already-loaded
     *   set to avoid an extra query — the policy never fetches anything itself.
     */
    fun isVisibleTo(
        visibility: String,
        subjectId: UUID,
        requesterId: UUID,
        contactIds: Set<UUID>
    ): Boolean = when (visibility.lowercase()) {
        "everyone" -> true
        "nobody" -> requesterId == subjectId
        "contacts" -> requesterId == subjectId || requesterId in contactIds
        else -> false
    }

    /**
     * Resolves the subset of [candidateIds] entitled to see [subjectId]'s presence, applying the rule
     * once over the candidate set. Used by the realtime broadcast path.
     *
     * - "everyone": all candidates
     * - "contacts": candidates that are also contacts (intersection)
     * - "nobody":   none (the subject is never in their own broadcast recipient set)
     * - unknown:    none (fail closed)
     */
    fun eligibleRecipients(
        visibility: String,
        candidateIds: Collection<UUID>,
        contactIds: Set<UUID>
    ): List<UUID> = when (visibility.lowercase()) {
        "everyone" -> candidateIds.toList()
        "contacts" -> candidateIds.filter { it in contactIds }
        "nobody" -> emptyList()
        else -> emptyList()
    }
}
