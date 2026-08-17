package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.FakePendingMessageCache
import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.protocol.WsMessage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The reconnect race behind #511, driven deterministically.
 *
 * The bug was reproduced on an emulator by switching language twice — which recreates the Activity
 * on purpose — and then watching the socket stay down for eight minutes while two messages went
 * silently into the offline queue. The interleaving that causes it is not reproducible by hand, so
 * these tests stage it directly: virtual time, an injected scope, and the two calls made in the
 * order that broke it.
 *
 * Every test drives the client on `backgroundScope`. The reconnect loop is deliberately infinite,
 * so `advanceUntilIdle()` would never return — time is always advanced by a bounded amount, and
 * `backgroundScope` is torn down by `runTest` without being awaited.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class) // advanceTimeBy(Long)
class WsClientReconnectTest {

    /**
     * Never resolves a token, so the connect loop parks in its WAITING_FOR_AUTH branch and no test
     * here ever touches the network. The branch still re-reads the token every couple of seconds,
     * which is what makes "did the loop restart?" observable.
     */
    private fun wsClient(
        scope: CoroutineScope,
        tokenProvider: () -> String? = { null },
        cache: com.muhabbet.app.data.local.PendingMessageCache? = null
    ): WsClient = WsClient(
        apiClient = ApiClient(
            FakeTokenStorage(),
            MockEngine {
                respond(
                    content = """{"data":null}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, Application.Json.toString())
                )
            }
        ),
        tokenProvider = tokenProvider,
        localCache = cache,
        scope = scope
    )

    @Test
    fun should_record_the_intent_to_stay_connected_before_connect_returns() = runTest {
        val ws = wsClient(backgroundScope)

        ws.connect()

        // Nothing dispatched has run yet — no runCurrent(), no time advanced. The flag must already
        // be set, because the whole bug is a disconnect() landing in exactly this window. While it
        // was assigned inside the launched coroutine, this assertion was false.
        assertTrue(
            ws.shouldReconnectForTest,
            "connect() must set the reconnect intent synchronously, not inside its coroutine"
        )
    }

    @Test
    fun should_keep_the_connection_when_a_replaced_composition_disconnects_late() = runTest {
        val ws = wsClient(backgroundScope)

        // The Activity that is going away connected at some point...
        val dying = ws.connect()
        // ...its replacement composes and connects (Compose does not order these for us)...
        val live = ws.connect()
        // ...and only now does the dead composition's onDispose finally run.
        ws.disconnect(dying)

        assertTrue(
            ws.shouldReconnectForTest,
            "A disconnect from a composition that has already been replaced must not tear down " +
                "the connection the new one just opened — that is #511"
        )
        assertNotEquals(ConnectionState.DISCONNECTED, ws.connectionState.value)
    }

    @Test
    fun should_still_tear_down_when_the_live_generation_disconnects() = runTest {
        val ws = wsClient(backgroundScope)

        val live = ws.connect()
        ws.disconnect(live)

        // The guard must not turn disconnect() into a no-op in the ordinary case: logging out and
        // backgrounding both depend on this working.
        assertFalse(ws.shouldReconnectForTest)
        assertSame(ConnectionState.DISCONNECTED, ws.connectionState.value)
    }

    @Test
    fun should_restart_a_connect_loop_that_has_stopped() = runTest {
        var tokenReads = 0
        val ws = wsClient(backgroundScope, tokenProvider = { tokenReads++; null })

        ws.connect()
        runCurrent()
        val readsWhileFirstLoopRan = tokenReads
        assertTrue(readsWhileFirstLoopRan > 0, "the connect loop should have started")

        // End the loop the way an uncaught Throwable would, leaving the client wanting a connection
        // with nothing running — the state #511 was actually found in, where the only thing still
        // alive was a heartbeat pinging into a null session every 30 seconds.
        ws.connectLoopForTest?.cancel()
        runCurrent()
        assertFalse(
            ws.connectLoopForTest?.isActive == true,
            "precondition: the loop is gone and the client still wants a connection"
        )

        // One heartbeat interval later, the watchdog should have noticed and started a new one.
        advanceTimeBy(HEARTBEAT_PLUS_A_MOMENT)
        runCurrent()

        assertTrue(
            ws.connectLoopForTest?.isActive == true,
            "the watchdog should have restarted the loop rather than leaving the app offline"
        )
        assertTrue(
            tokenReads > readsWhileFirstLoopRan,
            "the restarted loop should actually be running, not merely present"
        )
    }

    @Test
    fun should_not_run_two_connect_loops_at_once() = runTest {
        val ws = wsClient(backgroundScope)

        ws.connect()
        runCurrent()
        val first = ws.connectLoopForTest

        ws.connect()
        runCurrent()

        // Two live loops overwrite each other's session and heartbeat fields, and the watchdog
        // fires every 30s forever — so it must never be able to accumulate loops either.
        assertSame(
            first,
            ws.connectLoopForTest,
            "a second connect() must adopt the running loop instead of starting a rival"
        )
    }

    @Test
    fun should_not_let_the_watchdog_stack_up_loops_while_one_is_running() = runTest {
        val ws = wsClient(backgroundScope)

        ws.connect()
        runCurrent()
        val original = ws.connectLoopForTest

        // Several heartbeat intervals with the loop healthy and running.
        advanceTimeBy(HEARTBEAT_PLUS_A_MOMENT * 3)
        runCurrent()

        assertSame(
            original,
            ws.connectLoopForTest,
            "the watchdog must leave a running loop alone"
        )
    }

    @Test
    fun should_report_a_message_it_queued_as_queued_rather_than_as_a_failure() = runTest {
        val cache = FakePendingMessageCache()
        val ws = wsClient(backgroundScope, cache = cache)

        // Never connected, so there is no session and the body goes to the offline queue. The
        // distinct type is what lets ChatScreen leave the bubble on screen as a pending clock
        // instead of deleting it and claiming the send failed — it will go out on the next connect.
        assertFailsWith<MessageQueuedException> {
            ws.send(
                WsMessage.SendMessage(
                    requestId = "req-1",
                    messageId = "msg-1",
                    conversationId = "conv-1",
                    content = "ttl-testi",
                    contentType = ContentType.TEXT
                )
            )
        }

        // The half this test used to be missing. It ran with no cache at all and still asserted
        // "queued", so it passed while the message was being dropped — the fixture could not queue
        // anything. Naming the row is what makes the claim real.
        assertEquals(1, cache.queued.size, "the message should actually be in the queue")
        assertEquals("msg-1", cache.queued.single().messageId)
    }

    @Test
    fun should_not_claim_a_message_is_queued_when_there_is_nowhere_to_queue_it() = runTest {
        // No cache — the state a client is in before the local database is attached, and the state
        // every test here used to run in. `queuePendingMessage` returned silently and `send` threw
        // MessageQueuedException anyway, so ChatScreen left a pending clock on a message that had
        // been stored nowhere and would never be sent. It has to fail, and distinguishably.
        val ws = wsClient(backgroundScope, cache = null)

        assertFailsWith<MessageCouldNotBeQueuedException> {
            ws.send(
                WsMessage.SendMessage(
                    requestId = "req-nocache",
                    messageId = "msg-nocache",
                    conversationId = "conv-1",
                    content = "kuyruk yok",
                    contentType = ContentType.TEXT
                )
            )
        }
    }

    @Test
    fun should_not_claim_a_message_is_queued_when_the_queue_write_fails() = runTest {
        val ws = wsClient(backgroundScope, cache = FakePendingMessageCache(failOnInsert = true))

        // The third way it can fail, and the one that survives a working install: the insert
        // throws. It was caught, logged, and reported to the user as queued.
        assertFailsWith<MessageCouldNotBeQueuedException> {
            ws.send(
                WsMessage.SendMessage(
                    requestId = "req-fail",
                    messageId = "msg-fail",
                    conversationId = "conv-1",
                    content = "disk dolu",
                    contentType = ContentType.TEXT
                )
            )
        }
    }

    @Test
    fun should_still_let_a_caller_catch_the_view_once_refusal_by_its_general_type() = runTest {
        // ViewOnceNotQueueableException is now a MessageNotQueuedException. ChatScreen catches the
        // specific one to give the specific message; anything that only cares about the category
        // must keep working.
        val ws = wsClient(backgroundScope, cache = FakePendingMessageCache())

        assertFailsWith<MessageNotQueuedException> {
            ws.send(
                WsMessage.SendMessage(
                    requestId = "req-vo2",
                    messageId = "msg-vo2",
                    conversationId = "conv-1",
                    content = "Photo",
                    contentType = ContentType.IMAGE,
                    mediaUrl = "https://cdn.example/blob.jpg",
                    viewOnce = true
                )
            )
        }
    }

    @Test
    fun should_refuse_a_view_once_photo_rather_than_queue_it_unsealed() = runTest {
        val ws = wsClient(backgroundScope)

        // The offline queue has no `viewOnce` column, so `drainPendingMessages` rebuilds the frame
        // with the flag at its default of false. Queueing a sealed photo therefore delivered it as
        // an ordinary, permanent one on the next reconnect — #515 again, on the path where the user
        // is least likely to be watching. It must fail instead, with a type distinct from
        // MessageQueuedException so the caller removes the bubble and says so.
        assertFailsWith<ViewOnceNotQueueableException> {
            ws.send(
                WsMessage.SendMessage(
                    requestId = "req-vo",
                    messageId = "msg-vo",
                    conversationId = "conv-1",
                    content = "Photo",
                    contentType = ContentType.IMAGE,
                    mediaUrl = "https://cdn.example/blob.jpg",
                    viewOnce = true
                )
            )
        }
    }

    /**
     * The Activity-recreation path, from the UI's point of view rather than the socket's.
     *
     * The tests above assert the *connection* survives a recreation. This asserts the app is told
     * the truth about it. On the emulator the socket was verifiably alive — the backend held one
     * registration with no unregister, and a message sent through it landed in `messages` — while
     * the app displayed "No connection" for as long as it stayed open (#521).
     *
     * The cause is that `connect()` set CONNECTING unconditionally, and `startConnectLoop()` is
     * single-flighted: on a recreation it returns immediately because the surviving loop is still
     * running, and that loop is parked in its read and never revisits the line setting CONNECTED.
     * So the optimistic write was never corrected.
     */
    @Test
    fun should_not_overwrite_the_state_when_a_running_loop_is_reused() = runTest {
        val ws = wsClient(backgroundScope)
        ws.connect()
        runCurrent()

        // The loop is alive and parked awaiting a token. Whatever it has published is the truth;
        // a second connect() must not contradict it.
        val stateWhileRunning = ws.connectionState.value
        assertTrue(
            ws.connectLoopForTest?.isActive == true,
            "precondition: the first connect must leave a running loop for the second to reuse"
        )

        ws.connect()
        runCurrent()

        assertSame(
            stateWhileRunning,
            ws.connectionState.value,
            "reusing a running loop must leave the connection state alone — claiming CONNECTING " +
                "here is what made the strip say 'No connection' while messages were going through"
        )
    }

    private companion object {
        /** The heartbeat interval plus enough slack that the tick has definitely been dispatched. */
        const val HEARTBEAT_PLUS_A_MOMENT = 31_000L
    }
}
