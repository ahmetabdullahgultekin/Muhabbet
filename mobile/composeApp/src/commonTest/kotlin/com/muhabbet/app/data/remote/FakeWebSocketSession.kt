package com.muhabbet.app.data.remote

import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A scriptable server end of a WebSocket.
 *
 * Records everything the client writes. With [answerPings] it replies `pong` to every `ping`, the
 * way the real server does (`ChatWebSocketHandler`), which is also what proves to the client that
 * the token was accepted. With it off it plays a half-open TCP connection: writes succeed and
 * nothing ever comes back (#802). [ackSends] makes it answer every `message.send` with an OK ack.
 *
 * `incoming` stays open until [dropConnection], so the client parks reading it exactly as it does
 * against a healthy server.
 */
internal class FakeWebSocketSession(
    var answerPings: Boolean = true,
    var ackSends: Boolean = false
) : WebSocketSession {
    override val coroutineContext: CoroutineContext = EmptyCoroutineContext

    private val sent = Channel<Frame>(Channel.UNLIMITED)
    private val received = Channel<Frame>(Channel.UNLIMITED)
    private val log = mutableListOf<String>()

    /** Closes the client's writes too, as a socket the OS has torn down would. */
    var writesFail: Boolean = false

    override val incoming: ReceiveChannel<Frame> = received
    override val outgoing: SendChannel<Frame> = object : SendChannel<Frame> by sent {
        override suspend fun send(element: Frame) {
            if (writesFail) throw IllegalStateException("socket closed")
            sent.send(element)
            onClientFrame(element)
        }
    }
    override val extensions: List<WebSocketExtension<*>> = emptyList()
    override var masking: Boolean = false
    override var maxFrameSize: Long = Long.MAX_VALUE

    override suspend fun send(frame: Frame) = outgoing.send(frame)

    override suspend fun flush() = Unit

    @Suppress("OVERRIDE_DEPRECATION")
    override fun terminate() {
        sent.close()
        received.close()
    }

    /** True once either end has closed this socket. */
    var isDropped: Boolean = false
        private set

    private fun onClientFrame(frame: Frame) {
        if (frame is Frame.Close) {
            isDropped = true
            received.close()
            return
        }
        if (frame !is Frame.Text) return
        val text = frame.readText()
        log += text
        val message = runCatching { wsJson.decodeFromString<WsMessage>(text) }.getOrNull() ?: return
        when {
            message == WsMessage.Ping && answerPings -> serverSends(WsMessage.Pong)
            message is WsMessage.SendMessage && ackSends -> serverSends(
                WsMessage.ServerAck(
                    requestId = message.requestId,
                    messageId = message.messageId,
                    status = com.muhabbet.shared.protocol.AckStatus.OK,
                    serverTimestamp = 1L
                )
            )
        }
    }

    /** The server pushes [message] to the client. */
    fun serverSends(message: WsMessage) {
        received.trySend(Frame.Text(wsJson.encodeToString<WsMessage>(message)))
    }

    /** Ends the session the way a dropped network does: the client's read completes. */
    fun dropConnection() {
        isDropped = true
        received.close()
    }

    /** Everything the client has written so far, as text. */
    fun writtenText(): List<String> = log.toList()

    /** The `message.send` frames the client has written, decoded. */
    fun sentMessages(): List<WsMessage.SendMessage> =
        log.mapNotNull { runCatching { wsJson.decodeFromString<WsMessage>(it) }.getOrNull() as? WsMessage.SendMessage }
}
