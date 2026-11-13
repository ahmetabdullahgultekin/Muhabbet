package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.ui.connection.connectionStripText
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.connection_offline_strip
import com.muhabbet.composeapp.generated.resources.connection_upgrade_required_strip
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.protocol.ClientInfo
import com.muhabbet.shared.protocol.WireProtocol
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The app's half of protocol versioning (#873) and of the typed error catalogue (#883).
 */
class ClientProtocolTest {

    private fun client(status: HttpStatusCode, body: String, seen: MutableList<HttpRequestData> = mutableListOf()) =
        ApiClient(
            FakeTokenStorage(),
            MockEngine { request ->
                seen += request
                respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
        )

    @Test
    fun should_announce_this_builds_protocol_on_every_request() = runTest {
        val seen = mutableListOf<HttpRequestData>()
        val api = client(HttpStatusCode.OK, """{"data":"x","timestamp":"t"}""", seen)

        api.get<String>("/api/v1/users/me")

        val announced = ClientInfo.parseHeader(seen.single().headers[WireProtocol.CLIENT_HEADER])
        assertEquals(WireProtocol.VERSION, announced.protocolVersion)
    }

    @Test
    fun should_enter_the_upgrade_required_state_on_426() = runTest {
        val api = client(
            HttpStatusCode.UpgradeRequired,
            """{"error":{"code":"CLIENT_UPGRADE_REQUIRED","message":"x"},"timestamp":"t"}""",
        )
        assertFalse(api.upgradeRequired.value)

        val failure = assertFailsWith<ApiException> { api.get<String>("/api/v1/users/me") }

        assertEquals(ErrorCode.CLIENT_UPGRADE_REQUIRED, failure.code)
        assertTrue(failure.isUpgradeRequired)
        assertTrue(api.upgradeRequired.value)
    }

    @Test
    fun should_not_enter_the_upgrade_required_state_for_an_ordinary_failure() = runTest {
        val api = client(HttpStatusCode.Forbidden, """{"error":{"code":"MSG_NOT_MEMBER","message":"x"},"timestamp":"t"}""")

        assertFailsWith<ApiException> { api.get<String>("/api/v1/conversations") }

        assertFalse(api.upgradeRequired.value)
    }

    @Test
    fun should_type_a_code_from_a_newer_server_as_unknown_rather_than_failing() = runTest {
        val api = client(HttpStatusCode.Conflict, """{"error":{"code":"SOMETHING_ADDED_LATER","message":"x"},"timestamp":"t"}""")

        val failure = assertFailsWith<ApiException> { api.get<String>("/api/v1/conversations") }

        assertEquals(ErrorCode.UNKNOWN, failure.code)
    }

    @Test
    fun should_tell_the_user_to_update_rather_than_that_they_are_offline() {
        assertEquals(Res.string.connection_upgrade_required_strip, connectionStripText(ConnectionState.UPGRADE_REQUIRED))
        ConnectionState.entries.filter { it != ConnectionState.UPGRADE_REQUIRED }.forEach {
            assertEquals(Res.string.connection_offline_strip, connectionStripText(it), "$it")
        }
    }
}
