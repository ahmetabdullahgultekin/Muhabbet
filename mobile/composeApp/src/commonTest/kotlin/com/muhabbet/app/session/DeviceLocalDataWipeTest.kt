package com.muhabbet.app.session

import com.muhabbet.app.data.local.FakeTokenStorage
import com.muhabbet.app.data.local.SessionDataCache
import com.muhabbet.app.data.local.SessionDataGate
import com.muhabbet.app.data.remote.ApiClient
import com.muhabbet.app.data.remote.FakeWebSocketSession
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the wipe actually erases (#752), against the real transport rather than a recording.
 */
class DeviceLocalDataWipeTest {

    private class RecordingCache(private val fail: Boolean = false) : SessionDataCache {
        var cleared = 0
            private set

        override fun clearSessionData() {
            if (fail) throw IllegalStateException("database locked")
            cleared++
        }
    }

    @Test
    fun `should erase the cached conversations, messages and outbox when it runs`() = runTest {
        val cache = RecordingCache()
        val wipe = DeviceLocalDataWipe(cache, SessionDataGate(), clearTransportState = {}, storageContext = EmptyCoroutineContext)

        wipe.wipe()

        assertEquals(1, cache.cleared)
    }

    @Test
    fun `should not replay a receipt queued by the account that left when the next session connects`() = runTest {
        // A receipt queued while offline is replayed on the next connect. After a logout and a login
        // as somebody else, that connect is the other account's socket.
        val sessions = mutableListOf<FakeWebSocketSession>()
        val ws = WsClient(
            apiClient = ApiClient(FakeTokenStorage(), MockEngine { respond("", HttpStatusCode.OK) }),
            tokenProvider = { "a-token" },
            scope = backgroundScope,
            openSession = { FakeWebSocketSession().also { sessions.add(it) } }
        )
        ws.sendAck(WsMessage.AckMessage(messageId = "msg-1", conversationId = "conv-1", status = MessageStatus.DELIVERED))
        val wipe = DeviceLocalDataWipe(RecordingCache(), SessionDataGate(), ws::clearSessionState, EmptyCoroutineContext)

        wipe.wipe()
        ws.connect()
        runCurrent()

        assertTrue(
            sessions.single().writtenText().none { it.contains("message.ack") },
            "the queued receipt belonged to the previous account"
        )
    }

    @Test
    fun `should still clear the transport when the database refuses`() = runTest {
        var transportCleared = false
        val wipe = DeviceLocalDataWipe(
            RecordingCache(fail = true),
            SessionDataGate(),
            clearTransportState = { transportCleared = true },
            storageContext = EmptyCoroutineContext
        )

        wipe.wipe()

        assertTrue(transportCleared, "one failed step must not leave the others undone")
    }

    @Test
    fun `should finish erasing when the caller is cancelled in the middle of the wipe`() = runTest {
        // The wipe runs from a Compose effect that an Activity recreation or a quick re-login can
        // cancel. Abandoned halfway, it leaves exactly the data the user believed gone.
        val cache = RecordingCache()
        val transportStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var transportCleared = false
        val wipe = DeviceLocalDataWipe(cache, SessionDataGate(), clearTransportState = {
            transportStarted.complete(Unit)
            release.await()
            transportCleared = true
        }, storageContext = EmptyCoroutineContext)

        val job = launch { wipe.wipe() }
        transportStarted.await()
        job.cancel()
        release.complete(Unit)
        job.join()

        assertEquals(1, cache.cleared)
        assertTrue(transportCleared, "cancellation must not stop a wipe that has started")
    }

    @Test
    fun `should drop a write for the ended session when it arrives after the wipe`() = runTest {
        val gate = SessionDataGate()
        val before = gate.currentSession()
        val wipe = DeviceLocalDataWipe(RecordingCache(), gate, clearTransportState = {}, storageContext = EmptyCoroutineContext)

        wipe.wipe()
        var wrote = false
        val result = gate.writeIfCurrent(before) { wrote = true }

        assertEquals(null, result)
        assertTrue(!wrote, "a write from the previous session must not land after the wipe")
    }
}
