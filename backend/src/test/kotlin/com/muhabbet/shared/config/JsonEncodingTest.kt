package com.muhabbet.shared.config

import com.muhabbet.shared.model.UserProfile
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the serialization contract that #269 turned out to be about.
 *
 * `Json.Default` omits any field equal to its declared default, so `isOnline = false` disappeared
 * from the body instead of being sent as `false`. A Kotlin client decodes the two identically —
 * it re-applies the same default — which is exactly why nobody noticed. Anything that is not this
 * codebase sees a different API from the documented one, and for a privacy field "absent" and
 * "false" are not the same answer.
 */
class JsonEncodingTest {

    private val json = JsonConfig().json()

    @Test
    fun `a false boolean is sent as false rather than omitted`() {
        val hidden = UserProfile(
            id = "u1",
            displayName = "Test",
            avatarUrl = null,
            isOnline = false,
        )

        val encoded = json.encodeToString(hidden)

        assertTrue(
            encoded.contains("\"isOnline\":false"),
            "isOnline must be present and false; a user who hid their status got no key at all: $encoded"
        )
    }

    @Test
    fun `nulls are sent explicitly rather than omitted`() {
        val encoded = json.encodeToString(
            UserProfile(id = "u1", displayName = null, avatarUrl = null)
        )

        assertTrue(encoded.contains("\"lastSeenAt\":null"), "lastSeenAt was dropped: $encoded")
        assertTrue(encoded.contains("\"phoneNumber\":null"), "phoneNumber was dropped: $encoded")
    }

    @Test
    fun `an unknown field from a newer client does not fail the whole request`() {
        // Mobile updates out of step with the backend. An older server must tolerate a field it does
        // not know rather than rejecting the call.
        val decoded = json.decodeFromString<UserProfile>(
            """{"id":"u1","displayName":"Test","avatarUrl":null,"somethingNewerClientsSend":true}"""
        )

        assertEquals("u1", decoded.id)
    }
}
