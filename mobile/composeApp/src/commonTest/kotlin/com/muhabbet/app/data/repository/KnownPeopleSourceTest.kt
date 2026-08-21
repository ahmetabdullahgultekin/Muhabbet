package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeConversationCache
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.app.platform.DeviceContact
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.MemberRole
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Who a member picker may offer (#520).
 *
 * The bug this covers was not a crash: every picker in the app rendered a single control — "grant
 * contacts access" — when `READ_CONTACTS` was not held, so an account with two open conversations
 * could not put either of those two people into a group. The fix hangs entirely on the scoping
 * decision below, so that decision is what is pinned here.
 *
 * The boundary cuts both ways and both directions have already gone wrong once in this codebase:
 * #425 (do not weaken the contact-sync consent gate) and #507 (scoping so loose that every status
 * reached every account). What is opened up is exactly the relationship the **server can verify** —
 * you and this person share a DIRECT conversation — and nothing wider.
 */
class KnownPeopleSourceTest {

    private companion object {
        const val ME = "user-me"
        const val CONVERSATIONS = "/api/v1/conversations"
    }

    private fun participant(id: String, name: String?) = ParticipantResponse(
        userId = id,
        displayName = name,
        phoneNumber = null,
        avatarUrl = null,
        role = MemberRole.MEMBER,
        isOnline = false,
    )

    private fun conversation(
        id: String,
        type: ConversationType,
        participants: List<ParticipantResponse>,
    ) = ConversationResponse(
        id = id,
        type = type,
        participants = participants,
        unreadCount = 0,
        createdAt = "2026-08-16T10:00:00Z",
    )

    // ─── the scoping decision ────────────────────────────────

    @Test
    fun peopleFrom_offersTheOtherPartyOfEveryDirectConversation() {
        val people = KnownPeopleSource.peopleFrom(
            conversations = listOf(
                conversation("c1", ConversationType.DIRECT, listOf(participant(ME, "Ben"), participant("u2", "Ayşe"))),
                conversation("c2", ConversationType.DIRECT, listOf(participant(ME, "Ben"), participant("u3", "Bora"))),
            ),
            selfUserId = ME,
        )

        assertEquals(listOf("u2", "u3"), people.map { it.userId })
        assertEquals(listOf("Ayşe", "Bora"), people.map { it.displayName })
    }

    @Test
    fun peopleFrom_neverOffersTheCallerThemselves() {
        // The participant list of a direct conversation includes the caller, and a picker that
        // offers "yourself" produces a group creation the server rejects.
        val people = KnownPeopleSource.peopleFrom(
            conversations = listOf(
                conversation("c1", ConversationType.DIRECT, listOf(participant(ME, "Ben"), participant("u2", "Ayşe"))),
            ),
            selfUserId = ME,
        )

        assertEquals(listOf("u2"), people.map { it.userId })
    }

    @Test
    fun peopleFrom_neverHarvestsTheMembersOfAGroup() {
        // The deliberate boundary. Sharing a two-hundred-person group with somebody is not the same
        // as having chosen to talk to them; offering that roster in a picker would be #507 again,
        // one group at a time. A user who wants them can still type their number.
        val people = KnownPeopleSource.peopleFrom(
            conversations = listOf(
                conversation(
                    "g1",
                    ConversationType.GROUP,
                    listOf(participant(ME, "Ben"), participant("u9", "Stranger"), participant("u8", "Also stranger")),
                ),
            ),
            selfUserId = ME,
        )

        assertTrue(people.isEmpty(), "group co-members must not become picker candidates")
    }

    @Test
    fun peopleFrom_listsSomeoneOnceEvenWithSeveralConversations() {
        val people = KnownPeopleSource.peopleFrom(
            conversations = listOf(
                conversation("c1", ConversationType.DIRECT, listOf(participant(ME, "Ben"), participant("u2", "Ayşe"))),
                conversation("c2", ConversationType.DIRECT, listOf(participant(ME, "Ben"), participant("u2", "Ayşe"))),
            ),
            selfUserId = ME,
        )

        assertEquals(listOf("u2"), people.map { it.userId })
    }

    @Test
    fun peopleFrom_whenTheUserIdIsUnknown_stillOffersTheOtherParty() {
        // A missing user id must not empty the picker: the failure mode to avoid is the wall this
        // issue is about, and offering one extra row (yourself) is recoverable where offering
        // nothing is not.
        val people = KnownPeopleSource.peopleFrom(
            conversations = listOf(
                conversation("c1", ConversationType.DIRECT, listOf(participant(ME, "Ben"), participant("u2", "Ayşe"))),
            ),
            selfUserId = null,
        )

        assertEquals(listOf("Ayşe", "Ben"), people.map { it.displayName })
    }

    // ─── the wire ────────────────────────────────────────────

    @Test
    fun peopleWithDirectConversations_readsTheConversationListAndNothingElse() = runTest {
        // The whole point of #520: this path costs no permission, no consent and no contact sync. If
        // it ever starts touching /contacts/sync, an account that declined has lost group chat again.
        val requestedPaths = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedPaths += request.url.encodedPath
            respond(
                content = """{"data":{"items":[{"id":"c1","type":"DIRECT","participants":[""" +
                    """{"userId":"$ME","role":"MEMBER","isOnline":false},""" +
                    """{"userId":"u2","displayName":"Ayşe","role":"MEMBER","isOnline":false}""" +
                    """],"unreadCount":0,"createdAt":"2026-08-16T10:00:00Z"}],""" +
                    """"nextCursor":null,"hasMore":false},"timestamp":"2026-08-16T10:00:00Z"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val tokenStorage = FakeTokenStorage().apply { saveTokens("a", "r", ME, "device-1") }
        val source = KnownPeopleSource(
            conversationRepository = ConversationRepository(
                ApiClient(tokenStorage, engine),
                FakeConversationCache(),
            ),
            contactsProvider = ExplodingContactsProvider,
            tokenStorage = tokenStorage,
        )

        val people = source.peopleWithDirectConversations()

        assertEquals(listOf("u2"), people.map { it.userId })
        assertEquals(listOf(CONVERSATIONS), requestedPaths)
    }

    /**
     * Fails the test rather than the user if the conversation path ever reaches for contacts.
     *
     * A silent stub returning an empty list would let that regression pass green — the picker would
     * simply be short of rows, which is exactly how #520 escaped notice in the first place.
     */
    private object ExplodingContactsProvider : ContactsProvider {
        override fun hasPermission(): Boolean =
            throw AssertionError("the conversation path must not consult the contacts permission")

        override fun readContacts(): List<DeviceContact> =
            throw AssertionError("the conversation path must not read the address book")

        override fun openSystemSettings() =
            throw AssertionError("the conversation path must not send anyone to system settings")
    }
}
