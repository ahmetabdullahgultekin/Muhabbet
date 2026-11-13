package com.muhabbet.messaging.domain.port.`in`

import java.util.UUID

/**
 * Whose phone number a viewer may see on a conversation or participant payload (#902, KVKK).
 *
 * One answer for every surface that builds a `ParticipantResponse`, so the rule cannot drift
 * between the chat list, group creation and adding members the way it once drifted between
 * those and `GET /users/{id}` — which withheld the number while `GET /conversations` handed it
 * out for every co-member of every group.
 */
interface ResolvePhoneVisibilityUseCase {

    /**
     * The subset of [participantIds] whose phone number [viewerId] may receive.
     *
     * Always includes the viewer themselves when listed. Anyone else is included only when they
     * made themselves known to the viewer in a one-to-one conversation and have not blocked the
     * viewer. Co-membership of a group, channel, community or community announcement group never
     * qualifies on its own.
     */
    fun phoneVisibleParticipants(viewerId: UUID, participantIds: Collection<UUID>): Set<UUID>
}
