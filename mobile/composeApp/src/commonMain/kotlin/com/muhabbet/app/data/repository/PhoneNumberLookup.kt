package com.muhabbet.app.data.repository

import com.muhabbet.app.util.Log
import com.muhabbet.app.util.normalizeToE164
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.app.util.sha256Hex

/**
 * What happened when the user asked to reach a typed phone number.
 *
 * A sealed hierarchy rather than a nullable conversation id: "nobody is behind this number",
 * "that number is you" and "that is not a phone number" are three different things to say to the
 * user, and collapsing them into null is how the caller ends up inventing one message for all three.
 */
sealed interface PhoneLookupResult {

    /** The number belongs to a Muhabbet user and [conversationId] is the direct chat with them. */
    data class Opened(val conversationId: String, val displayName: String?) : PhoneLookupResult

    /**
     * The number belongs to a Muhabbet user and nothing has been done about it yet.
     *
     * Separate from [Opened] because a group picker wants the person, not a chat with them: creating
     * a direct conversation as a side effect of adding somebody to a group would leave the user with
     * an empty one-to-one thread they never asked for (#520).
     */
    data class Found(
        val userId: String,
        val displayName: String?,
        val avatarUrl: String?,
    ) : PhoneLookupResult

    /** A usable number that no Muhabbet account claims. The caller offers an invite. */
    data object NotOnMuhabbet : PhoneLookupResult

    /**
     * The caller's own number.
     *
     * Worth its own case because the server deliberately filters the requesting user out of a
     * contact sync ([ContactSyncService] "Exclude the requesting user's own hash"), so a self
     * lookup is indistinguishable from an unregistered one at the wire level — and telling someone
     * their own number is "not on Muhabbet", while they are using Muhabbet, is nonsense.
     */
    data object OwnNumber : PhoneLookupResult

    /** Not a number this app can dial. Produced without touching the network. */
    data object InvalidNumber : PhoneLookupResult
}

/**
 * Reaching someone by typing their phone number, for people who are not in the device address book.
 *
 * Contact discovery previously had exactly one source: the address book. Anyone holding your number
 * but absent from your contacts could not be contacted at all, which is the likeliest explanation
 * for 3 accounts and 2 conversations in production (#389).
 *
 * Deliberately client-only. A single-number lookup is a contact sync with a one-element list, so
 * `POST /api/v1/contacts/sync` and `POST /api/v1/conversations` already answer this completely; a
 * dedicated endpoint would have bought nothing but another production deploy.
 *
 * Lives outside the composable so the whole decision — normalise, hash, match, create — is testable
 * against a real [ConversationRepository] over a mock engine, rather than only through a screen no
 * test on this host can render.
 */
class PhoneNumberLookup(
    private val conversationRepository: ConversationRepository,
    private val authRepository: AuthRepository,
) {

    private companion object {
        const val TAG = "PhoneNumberLookup"
    }

    /**
     * Resolves [rawNumber] and, when it belongs to a Muhabbet user, returns the direct conversation
     * with them.
     *
     * Throws whatever the repositories throw — an [com.muhabbet.app.data.remote.ApiException] on a
     * rejected request above all. A server that says no must not arrive here as
     * [PhoneLookupResult.NotOnMuhabbet]: that is the failure-shaped-like-success this codebase has
     * had to remove nineteen times over.
     */
    suspend fun startChatWith(rawNumber: String): PhoneLookupResult {
        val match = findByNumber(rawNumber)
        if (match !is PhoneLookupResult.Found) return match

        // Idempotent server-side: ConversationService.createConversation looks up
        // findDirectConversation(low, high) first and returns the existing row without saving
        // (MessagingServiceTest "should return existing conversation when direct conversation
        // already exists"). So typing the number of someone already in the list re-opens that chat
        // instead of creating a duplicate, and no client-side de-duplication is needed.
        val conversation = conversationRepository.createDirectConversation(match.userId)
        return PhoneLookupResult.Opened(conversation.id, match.displayName)
    }

    /**
     * Who is behind [rawNumber], and nothing more.
     *
     * The half of [startChatWith] that has no side effects, split out for the group and add-member
     * pickers (#520): they need the person, and creating a direct conversation on the way would hand
     * the user an empty chat they never asked for. Everything the two share — normalisation,
     * hashing, matching the hash we actually asked about, and the own-number check — lives here, so
     * the two paths cannot drift into two different answers for the same number.
     *
     * Returns [PhoneLookupResult.Found] on a hit, and otherwise one of the three endings that need
     * no caller: [PhoneLookupResult.InvalidNumber], [PhoneLookupResult.OwnNumber],
     * [PhoneLookupResult.NotOnMuhabbet].
     */
    suspend fun findByNumber(rawNumber: String): PhoneLookupResult {
        // Reuses the address-book normaliser rather than adding a second one — it already handles
        // 05XX / 5XX / 90XX / +90XX with spaces and dashes, and two normalisers that disagree by one
        // format would silently hash the same person two different ways.
        val e164 = normalizeToE164(rawNumber) ?: return PhoneLookupResult.InvalidNumber
        val phoneHash = sha256Hex(e164)

        val match = conversationRepository.syncContacts(listOf(phoneHash))
            .matchedContacts
            // Matched on the hash we asked about, not merely "the first row". One hash can only
            // come back as one contact, and pinning it means a surprising response opens no chat
            // rather than the wrong person's.
            .firstOrNull { it.phoneHash == phoneHash }
            ?: return if (isOwnNumber(e164)) PhoneLookupResult.OwnNumber else PhoneLookupResult.NotOnMuhabbet

        return PhoneLookupResult.Found(match.userId, match.displayName, match.avatarUrl)
    }

    /**
     * Whether [e164] is the caller's own number.
     *
     * Asked only when the sync matched nobody, so the common paths cost one request, not two.
     *
     * A failure here is logged and answered "no": the sync already succeeded, so the primary
     * finding — nobody is behind this number — stands, and downgrading a usable invite prompt to an
     * error over a secondary lookup would be the worse trade. The cost is that a self lookup during
     * a transient failure reads as "not on Muhabbet". Not silent, and not load-bearing.
     */
    private suspend fun isOwnNumber(e164: String): Boolean {
        val ownNumber = runCatchingCancellable { authRepository.getProfile().phoneNumber }
            .onFailure { Log.e(TAG, "Could not read own profile to identify a self-lookup", it) }
            .getOrNull()
            ?: return false
        return normalizeToE164(ownNumber) == e164
    }
}
