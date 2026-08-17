package com.muhabbet.app.data.repository

import com.muhabbet.app.data.local.FakeConversationCache
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.app.util.sha256Hex
import com.muhabbet.shared.dto.ContactSyncRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Reaching a person by typed phone number (#389) — the flow that decides whether a number opens a
 * chat, offers an invite, or is refused before it costs a request.
 *
 * Driven through a real [ApiClient] over a `MockEngine`, like [CommunityRepositoryTest], so the
 * paths, request bodies and envelope decoding are the production ones. There is no emulator on this
 * host, so this is the only place the flow can be exercised at all.
 */
class PhoneNumberLookupTest {

    private companion object {
        const val CONTACTS_SYNC = "/api/v1/contacts/sync"
        const val CONVERSATIONS = "/api/v1/conversations"
        const val OWN_PROFILE = "/api/v1/users/me"

        /** The same person written four ways, as Turkish address books and Turkish users write them. */
        val TURKISH_FORMATS = listOf(
            "+905321234567",
            "0532 123 45 67",
            "532-123-45-67",
            "90 532 123 45 67",
        )
        const val E164 = "+905321234567"
    }

    /**
     * A backend that records what it was asked and answers plausibly.
     *
     * [matchRequestedNumber] switches the one interesting server behaviour: whether the hash the
     * client sent belongs to somebody. Echoing the *requested* hash back rather than a fixture value
     * is deliberate — it means the match only happens if the client hashed what it claimed to.
     */
    private class Backend(
        matchRequestedNumber: Boolean = false,
        ownPhoneNumber: String = "+905550000001",
        syncStatus: HttpStatusCode = HttpStatusCode.OK,
        /** Lets one test answer with a hash the client never asked about. */
        matchedHashOverride: String? = null,
    ) {
        val requestedPaths = mutableListOf<String>()
        val requestedHashes = mutableListOf<String>()

        private val json = Json { ignoreUnknownKeys = true }

        private val engine = MockEngine { request ->
            val path = request.url.encodedPath
            requestedPaths += path
            when (path) {
                CONTACTS_SYNC -> {
                    val body = json.decodeFromString<ContactSyncRequest>(
                        request.body.toByteArray().decodeToString()
                    )
                    requestedHashes += body.phoneHashes
                    val hash = matchedHashOverride ?: body.phoneHashes.first()
                    val matches = if (matchRequestedNumber || matchedHashOverride != null) {
                        """[{"userId":"user-2","phoneHash":"$hash","displayName":"Ayşe Yılmaz"}]"""
                    } else {
                        "[]"
                    }
                    jsonResponse(
                        syncStatus,
                        if (syncStatus == HttpStatusCode.OK) {
                            """{"data":{"matchedContacts":$matches},"timestamp":"2026-08-15T10:00:00Z"}"""
                        } else {
                            """{"error":{"code":"INTERNAL_ERROR","message":"Beklenmeyen hata"},"timestamp":"2026-08-15T10:00:00Z"}"""
                        }
                    )
                }

                CONVERSATIONS -> jsonResponse(
                    HttpStatusCode.OK,
                    """{"data":{"id":"conv-9","type":"DIRECT","participants":[],"unreadCount":0,""" +
                        """"createdAt":"2026-08-15T10:00:00Z"},"timestamp":"2026-08-15T10:00:00Z"}"""
                )

                OWN_PROFILE -> jsonResponse(
                    HttpStatusCode.OK,
                    """{"data":{"id":"user-1","phoneNumber":"$ownPhoneNumber","displayName":"Ben",""" +
                        """"avatarUrl":null},"timestamp":"2026-08-15T10:00:00Z"}"""
                )

                else -> jsonResponse(HttpStatusCode.NotFound, """{"error":{"code":"NOT_FOUND","message":"$path"}}""")
            }
        }

        val lookup: PhoneNumberLookup = ApiClient(FakeTokenStorage(), engine).let { apiClient ->
            PhoneNumberLookup(
                conversationRepository = ConversationRepository(apiClient, FakeConversationCache()),
                authRepository = AuthRepository(apiClient, FakeTokenStorage()),
            )
        }
    }

    @Test
    fun startChatWith_whenTheNumberBelongsToAUser_opensTheDirectConversation() = runTest {
        val backend = Backend(matchRequestedNumber = true)

        val result = backend.lookup.startChatWith("0532 123 45 67")

        assertEquals(PhoneLookupResult.Opened("conv-9", "Ayşe Yılmaz"), result)
        assertEquals(listOf(CONTACTS_SYNC, CONVERSATIONS), backend.requestedPaths)
    }

    @Test
    fun startChatWith_whenNobodyHasThatNumber_createsNoConversation() = runTest {
        // The invite path. Creating a conversation here would be a chat with nobody in it, and the
        // user would sit waiting for a reply from an account that does not exist.
        val backend = Backend(matchRequestedNumber = false)

        val result = backend.lookup.startChatWith("0532 123 45 67")

        assertEquals(PhoneLookupResult.NotOnMuhabbet, result)
        assertTrue(CONVERSATIONS !in backend.requestedPaths, "no conversation may be created")
    }

    @Test
    fun startChatWith_whenTheNumberIsTheCallersOwn_saysSoRatherThanOfferingAnInvite() = runTest {
        // The server strips the caller's own hash out of a contact sync, so a self-lookup comes back
        // empty and is indistinguishable from an unregistered number on the wire. Telling a user
        // their own number is "not on Muhabbet" while they are using Muhabbet is nonsense, so the
        // own-number check runs on the miss path.
        val backend = Backend(matchRequestedNumber = false, ownPhoneNumber = "0532 123 45 67")

        val result = backend.lookup.startChatWith("+90 532 123 45 67")

        assertEquals(PhoneLookupResult.OwnNumber, result)
        assertTrue(CONVERSATIONS !in backend.requestedPaths, "no conversation may be created")
    }

    @Test
    fun startChatWith_whenTheNumberIsMalformed_neverReachesTheNetwork() = runTest {
        val backend = Backend(matchRequestedNumber = true)

        listOf("", "   ", "abc", "123", "0532 123 45").forEach { malformed ->
            assertEquals(PhoneLookupResult.InvalidNumber, backend.lookup.startChatWith(malformed), malformed)
        }

        assertTrue(backend.requestedPaths.isEmpty(), "unusable input must cost no request")
    }

    @Test
    fun startChatWith_acrossTurkishNumberFormats_sendsOneAndTheSameHash() = runTest {
        // Every format the address-book sync already accepts must resolve to the same person here,
        // or the same contact is two different hashes depending on where they were typed.
        val backend = Backend(matchRequestedNumber = true)

        TURKISH_FORMATS.forEach { backend.lookup.startChatWith(it) }

        assertEquals(List(TURKISH_FORMATS.size) { sha256Hex(E164) }, backend.requestedHashes)
    }

    @Test
    fun startChatWith_whenTheServerAnswersWithAnUnrelatedContact_opensNoChat() = runTest {
        // Defence for the one operation here that cannot be undone by the user: opening a private
        // chat with a stranger. A row whose hash is not the one asked about is not an answer.
        val backend = Backend(matchedHashOverride = sha256Hex("+905559998877"))

        val result = backend.lookup.startChatWith("0532 123 45 67")

        assertEquals(PhoneLookupResult.NotOnMuhabbet, result)
        assertTrue(CONVERSATIONS !in backend.requestedPaths, "no conversation may be created")
    }

    // ─── findByNumber: the same lookup with no side effects (#520) ───────────────────────────────

    @Test
    fun findByNumber_whenTheNumberBelongsToAUser_returnsThePersonAndOpensNoChat() = runTest {
        // What the group and add-member pickers call. Creating a direct conversation on the way to
        // adding somebody to a group would leave the user with an empty one-to-one thread they never
        // asked for, so the absence of the second request is the assertion that matters.
        val backend = Backend(matchRequestedNumber = true)

        val result = backend.lookup.findByNumber("0532 123 45 67")

        assertEquals(PhoneLookupResult.Found("user-2", "Ayşe Yılmaz", null), result)
        assertEquals(listOf(CONTACTS_SYNC), backend.requestedPaths)
    }

    @Test
    fun findByNumber_answersTheSameThreeEndingsAsStartChatWith() = runTest {
        // The two paths share one implementation precisely so they cannot drift into two different
        // answers for the same number.
        assertEquals(
            PhoneLookupResult.NotOnMuhabbet,
            Backend(matchRequestedNumber = false).lookup.findByNumber("0532 123 45 67"),
        )
        assertEquals(
            PhoneLookupResult.OwnNumber,
            Backend(matchRequestedNumber = false, ownPhoneNumber = "0532 123 45 67")
                .lookup.findByNumber("+90 532 123 45 67"),
        )
        val malformed = Backend(matchRequestedNumber = true)
        assertEquals(PhoneLookupResult.InvalidNumber, malformed.lookup.findByNumber("abc"))
        assertTrue(malformed.requestedPaths.isEmpty(), "unusable input must cost no request")
    }

    @Test
    fun findByNumber_whenTheServerAnswersWithAnUnrelatedContact_findsNobody() = runTest {
        // Same defence as startChatWith. A row whose hash is not the one asked about is not an
        // answer, and here it would put a stranger into somebody's group.
        val backend = Backend(matchedHashOverride = sha256Hex("+905559998877"))

        assertEquals(PhoneLookupResult.NotOnMuhabbet, backend.lookup.findByNumber("0532 123 45 67"))
    }

    @Test
    fun startChatWith_whenTheServerRejectsTheLookup_failsInsteadOfOfferingAnInvite() = runTest {
        // Since #374 a non-2xx throws. Swallowing it into NotOnMuhabbet would invite a user to
        // invite somebody who is already here — a failure wearing a success's clothes.
        val backend = Backend(syncStatus = HttpStatusCode.InternalServerError)

        val failure = assertFailsWith<ApiException> { backend.lookup.startChatWith("0532 123 45 67") }

        assertEquals(500, failure.status)
        assertEquals("INTERNAL_ERROR", failure.code)
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonResponse(
    status: HttpStatusCode,
    body: String,
) = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
