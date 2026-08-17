package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.util.normalizeToE164
import com.muhabbet.app.util.sha256Hex
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Somebody a picker may offer, reduced to what a row needs to draw.
 *
 * Deliberately not [com.muhabbet.shared.dto.MatchedContact]: that type carries a `phoneHash`, which
 * means something only to the address-book sync, and it cannot describe a person who arrived from a
 * conversation rather than from the phone. One type for "a person you can pick" keeps a picker from
 * having to know where each row came from.
 */
data class KnownPerson(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
)

/**
 * Where a member picker gets its candidates.
 *
 * This exists because of #520: every member picker in the app was gated on `READ_CONTACTS`, so an
 * account with two open conversations was shown a wall reading "Rehber Erişimi Ver" and could not
 * put either of those two people into a group. The data was already on the device — the picker
 * simply never looked at it.
 *
 * The two sources here are not equivalent and are not treated as such. One is a relationship the
 * **server can verify** (you and this person share a conversation); the other is a claim only the
 * client can make (this number is in my address book), and it costs other people's phone numbers to
 * establish. Hence the asymmetry: the first is always available, the second needs two gates.
 */
class KnownPeopleSource(
    private val conversationRepository: ConversationRepository,
    private val contactsProvider: ContactsProvider,
    private val tokenStorage: TokenStorage,
) {

    /**
     * The other party of every direct conversation, sorted by name.
     *
     * **Direct conversations only, and that boundary is the point.** The server can verify that two
     * accounts share a DIRECT conversation; it cannot verify anything a client claims about its
     * address book or its permission state. #507 was the opposite failure — scoping so loose that
     * every status reached every account — so what is opened up here is exactly the relationship the
     * user established themselves by messaging the person, and nothing wider. Co-members of a group
     * are **not** included: being in a two-hundred-person group with someone is not the same as
     * having chosen to talk to them, and harvesting that roster into a picker would recreate #507
     * one group at a time.
     *
     * No permission, no consent, and no request beyond the conversation list the app already loads
     * and caches — which is why this path still works with contacts access declined.
     *
     * Reads one page rather than following the cursor: the picker is a starting point, not an
     * archive, and anyone further down than [PAGE_SIZE] conversations is reachable by typing their
     * number. Following pagination here would turn opening the picker into an unbounded number of
     * requests.
     */
    suspend fun peopleWithDirectConversations(): List<KnownPerson> =
        peopleFrom(
            conversations = conversationRepository.getConversations(limit = PAGE_SIZE).items,
            selfUserId = tokenStorage.getUserId(),
        )

    /**
     * The address-book half: hash every number on the phone and ask the server which ones it knows.
     *
     * **Call this only with both the OS permission and the recorded consent (#425).** This is the
     * call that puts hashes derived from other people's phone numbers on the wire, and those people
     * are not users of this service and agreed to nothing. The permission authorises reading the
     * address book on the device; it does not authorise sending anything derived from it anywhere.
     * `CreateGroupScreen` checked only the first of the two until #520.
     *
     * An empty address book is a real answer and returns an empty list without a request, rather
     * than a request that asks about nobody.
     */
    suspend fun peopleFromDeviceContacts(): List<KnownPerson> {
        val hashes = withContext(Dispatchers.Default) {
            contactsProvider.readContacts().mapNotNull { contact ->
                val digits = contact.phoneNumber.filter { c -> c.isDigit() || c == '+' }
                normalizeToE164(digits)?.let { sha256Hex(it) }
            }
        }
        if (hashes.isEmpty()) return emptyList()
        return conversationRepository.syncContacts(hashes).matchedContacts
            .map { KnownPerson(it.userId, it.displayName, it.avatarUrl) }
            .sortedBy { it.displayName ?: "" }
    }

    internal companion object {
        const val PAGE_SIZE = 50

        /**
         * Pure, so the scoping decision above can be tested without a backend.
         *
         * Filtering out [selfUserId] matters even though a direct conversation has only two members:
         * the participant list includes the caller, and a picker that offers "yourself" produces a
         * group the server rejects.
         */
        fun peopleFrom(
            conversations: List<ConversationResponse>,
            selfUserId: String?,
        ): List<KnownPerson> =
            conversations
                .filter { it.type == ConversationType.DIRECT }
                .flatMap { it.participants }
                .filter { it.userId != selfUserId }
                .map { KnownPerson(it.userId, it.displayName, it.avatarUrl) }
                .distinctBy { it.userId }
                .sortedBy { it.displayName ?: "" }
    }
}
