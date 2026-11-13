package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.outbox.FakeOutboxStore
import com.muhabbet.app.data.outbox.Outbox
import com.muhabbet.app.data.outbox.OutboxPolicy
import com.muhabbet.app.data.remote.connection.ConnectionPolicy
import com.muhabbet.app.data.sync.MissedMessageCatchUp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.TestScope

/**
 * A [WsClient] wired to fakes on virtual time: scripted sockets, an in-memory outbox table, a
 * request-counting HTTP engine and a recording token refresher.
 *
 * Everything runs on `backgroundScope`, because a client that wants a connection never becomes
 * idle — tests advance time by bounded amounts instead of `advanceUntilIdle()`.
 */
internal class WsClientHarness(
    test: TestScope,
    var token: String? = "a-token",
    /** Whether the next sockets answer pings (a healthy server) or stay silent (half-open). */
    var serverAnswers: Boolean = true,
    val store: FakeOutboxStore? = FakeOutboxStore(),
    catchUp: MissedMessageCatchUp? = null,
    outboxPolicy: OutboxPolicy = OutboxPolicy()
) {
    val sessions = mutableListOf<FakeWebSocketSession>()
    val openedAt = mutableListOf<Long>()
    var httpRequests = 0
        private set
    var refreshes = 0
        private set

    /** Lets a test script what a freshly opened socket does before the client reads it. */
    var onOpen: (FakeWebSocketSession) -> Unit = {}

    val outbox: Outbox? = store?.let {
        Outbox(it, test.backgroundScope, clock = { test.testScheduler.currentTime }, policy = outboxPolicy)
    }

    val client = WsClient(
        apiClient = ApiClient(
            FakeTokenStorage(),
            MockEngine {
                httpRequests++
                respond("""{"data":null}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, Application.Json.toString()))
            }
        ),
        tokenProvider = { token },
        outbox = outbox,
        scope = test.backgroundScope,
        openSession = {
            openedAt += test.testScheduler.currentTime
            FakeWebSocketSession(answerPings = serverAnswers).also { sessions += it; onOpen(it) }
        },
        catchUp = catchUp,
        refreshAuth = { refreshes++ },
        clock = { test.testScheduler.currentTime },
        policy = ConnectionPolicy()
    )

    /** Sockets the client has not been told are closed. */
    val openSessions: Int get() = sessions.count { !it.isDropped }
}
