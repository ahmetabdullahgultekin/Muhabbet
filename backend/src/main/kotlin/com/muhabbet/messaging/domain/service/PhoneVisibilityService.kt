package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.port.`in`.ResolvePhoneVisibilityUseCase
import com.muhabbet.messaging.domain.port.out.BlockPolicyPort
import com.muhabbet.messaging.domain.port.out.ConversationRepository
import java.util.UUID

/**
 * The single server-side decision on phone-number disclosure between users (#902).
 *
 * The rule, and why each part of it is there:
 * - **You always see your own number.** It is yours.
 * - **Someone else's number only after they reached you one-to-one** — they opened a DIRECT
 *   conversation with you or sent you a message in one
 *   ([ConversationRepository.findDirectCounterpartsWhoDisclosed]). That is the relationship the
 *   rest of the app already treats as chosen (`KnownPeopleSource` on the client), and the one in
 *   which a phone-number messenger discloses the number anyway: whoever messages you did so from
 *   their number. Group membership is not a choice to tell every member your number — and since
 *   community announcement groups enrol every community member and a community can be joined by
 *   invite link, counting it would turn an invite link into a phone-number export.
 * - **Never to someone they have blocked.** Only that direction: the block is the target asking
 *   to be done with the viewer. A viewer who blocked *them* already had their number and gains
 *   nothing private from keeping it on screen, whereas withholding it would quietly rename the
 *   chat they may want to report.
 *
 * Batched: one membership query and at most one block query per call, whatever the page size —
 * this runs on `GET /conversations`, the app's busiest call.
 *
 * Deliberately not the `contacts` / `everyone` vocabulary of `UserController.resolveVisibility`:
 * there is no user setting for phone visibility yet, and the safe default for a field the user
 * never chose to publish is the narrow one. A setting, if one is added, widens this — it does not
 * replace the block or the self rule.
 */
class PhoneVisibilityService(
    private val conversationRepository: ConversationRepository,
    private val blockPolicy: BlockPolicyPort
) : ResolvePhoneVisibilityUseCase {

    override fun phoneVisibleParticipants(viewerId: UUID, participantIds: Collection<UUID>): Set<UUID> {
        val self = if (viewerId in participantIds) setOf(viewerId) else emptySet()
        val others = participantIds.filterTo(HashSet()) { it != viewerId }
        if (others.isEmpty()) return self

        val disclosed = conversationRepository.findDirectCounterpartsWhoDisclosed(viewerId, others)
        if (disclosed.isEmpty()) return self

        return self + (disclosed - blockPolicy.findBlockedBy(viewerId, disclosed))
    }
}
