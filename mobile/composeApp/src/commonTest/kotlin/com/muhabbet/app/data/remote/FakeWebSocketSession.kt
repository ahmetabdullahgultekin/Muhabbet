package com.muhabbet.app.data.remote

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A socket that records what the client writes to it and never delivers anything back.
 *
 * Ktor's `MockEngine` does not implement the WebSocket capability, so there has never been a way to
 * observe the frames [WsClient] puts on the wire when a session comes up — the two queue drains, and
 * the focus re-assert of #667. `WsClient` takes an `openSession` lambda for exactly this.
 *
 * `incoming` is left open rather than closed, so the connect loop parks reading it exactly as it
 * does against a healthy server instead of falling through to the reconnect backoff.
 */
internal class FakeWebSocketSession : WebSocketSession {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext

    private val sent = Channel<Frame>(Channel.UNLIMITED)
    private val received = Channel<Frame>(Channel.UNLIMITED)

    override val incoming: ReceiveChannel<Frame> = received
    override val outgoing: SendChannel<Frame> = sent
    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE

    override suspend fun send(frame: Frame) {
        sent.send(frame)
    }

    override suspend fun flush() = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun terminate() {
        sent.close()
        received.close()
    }

    /**
     * Ends the session the way a dropped network does: the client's read of [incoming] completes,
     * it sees the socket as closed and goes round its reconnect loop.
     */
    fun dropConnection() {
        received.close()
    }

    /** Everything written so far, as text, drained from the channel. */
    fun writtenText(): List<String> = buildList {
        while (true) {
            val frame = sent.tryReceive().getOrNull() ?: break
            if (frame is Frame.Text) add(frame.readText())
        }
    }
}
