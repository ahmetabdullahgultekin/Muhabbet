package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #747: the WebSocket token moved from `?token=` to the upgrade request's `Authorization` header.
 *
 * The trap this pins: Ktor's bearer provider does not *add* a header beside one already set, it
 * *replaces* it with the token it cached at its last load or refresh. Left on for `/ws`, the header
 * WsClient sets from storage would be silently swapped for that cached one — which after a re-login
 * or an out-of-band refresh is not the token storage holds. The query parameter it replaces could
 * not be overwritten that way, so excluding `/ws` from the plugin is what keeps behaviour identical.
 */
class WsUpgradeAuthHeaderTest {

    private class Seen {
        val authByPath = mutableMapOf<String, List<String>>()
    }

    private fun client(storage: FakeTokenStorage, seen: Seen) = ApiClient(storage, MockEngine { request ->
        seen.authByPath[request.url.encodedPath] = request.headers.getAll(HttpHeaders.Authorization).orEmpty()
        respond("", HttpStatusCode.OK)
    })

    @Test
    fun wsUpgrade_carriesTheTokenWsClientSet_notThePluginsCachedOne() = runTest {
        val storage = FakeTokenStorage().apply { saveTokens("cached-access", "refresh", "u", "d") }
        val seen = Seen()
        val api = client(storage, seen)

        // Any REST call makes the plugin load, and cache, the token storage held at that moment.
        api.httpClient.get("${ApiClient.BASE_URL}/api/v1/users/me")
        storage.saveTokens("fresh-access", "refresh", "u", "d")

        api.httpClient.get("${ApiClient.BASE_URL}${ApiClient.WS_PATH}") {
            header(HttpHeaders.Authorization, "Bearer fresh-access")
        }

        assertEquals(listOf("Bearer cached-access"), seen.authByPath["/api/v1/users/me"])
        assertEquals(
            listOf("Bearer fresh-access"),
            seen.authByPath[ApiClient.WS_PATH],
            "exactly one Authorization header, and it is the one WsClient set",
        )
    }
}
