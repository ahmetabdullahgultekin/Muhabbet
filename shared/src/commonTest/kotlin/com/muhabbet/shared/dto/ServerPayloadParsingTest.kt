package com.muhabbet.shared.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Decodes payloads captured from the running server.
 *
 * The backend runs with `spring.jackson.default-property-inclusion: non_null`, so **any field it
 * leaves null is absent from the JSON entirely**. kotlinx.serialization requires a key to be present
 * for a nullable property unless that property has a default, and neither `ignoreUnknownKeys` nor
 * `isLenient` changes that. So every optional field the server omitted used to throw
 * `MissingFieldException` inside the repository, land in the caller's `catch`, and surface as a
 * generic error — the screen simply did nothing.
 *
 * Creating a community was the visible case: the POST returned 201, the row was in the database, and
 * the create form sat there unchanged. Thirty nullable fields across fourteen DTOs had the same
 * shape.
 *
 * The payloads below are copied verbatim from the deployed API, including which keys it omits.
 */
class ServerPayloadParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun `decodes the create-community response the server actually sends`() {
        // Verbatim from POST /api/v1/communities: no description, no avatarUrl, no groupCount,
        // no memberCount — and a createdBy the client does not declare.
        val payload = """
            {"data":{"id":"3b7ad483-4851-40b9-8e70-c134c04e9f42","name":"DtoTest3",
            "createdBy":"d8dfd64f-7a83-48aa-b7f9-6ffc3b5e0617",
            "createdAt":"2026-08-12T15:36:17.930153404Z"},
            "timestamp":"2026-08-12T15:36:17.939054591Z"}
        """.trimIndent()

        val response = json.decodeFromString(
            ApiResponse.serializer(CommunityResponse.serializer()),
            payload,
        )

        val community = requireNotNull(response.data) { "data must decode" }
        assertEquals("DtoTest3", community.name)
        assertNull(community.description)
        assertNull(community.avatarUrl)
        // Absent counts default rather than failing the whole parse.
        assertEquals(0, community.groupCount)
        assertEquals(0, community.memberCount)
    }

    @Test
    fun `decodes a conversation whose name is omitted`() {
        // Direct conversations have no name, so the server omits the key rather than sending null.
        val payload = """
            {"data":{"id":"a54a185a-cd6b-4920-b60c-dbc5b7d57830","type":"DIRECT",
            "participants":[],"unreadCount":0,"createdAt":"2026-08-12T14:00:00Z"},
            "timestamp":"2026-08-12T14:00:00Z"}
        """.trimIndent()

        val response = json.decodeFromString(
            ApiResponse.serializer(ConversationResponse.serializer()),
            payload,
        )

        assertNull(requireNotNull(response.data).name)
    }

    @Test
    fun `decodes an error envelope with no data`() {
        val payload =
            """{"error":{"code":"AUTH_OTP_INVALID","message":"gecersiz"},"timestamp":"2026-01-01T00:00:00Z"}"""

        val response = json.decodeFromString(
            ApiResponse.serializer(CommunityResponse.serializer()),
            payload,
        )

        assertNull(response.data)
        assertEquals("AUTH_OTP_INVALID", response.error?.code)
    }
}
