package com.muhabbet.app.data.remote

import com.muhabbet.app.crypto.MessageEncryptor
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.concurrent.Volatile

/**
 * Everything the client receives, decoded, decrypted and de-duplicated, on one [incoming] stream.
 *
 * Live frames ([read]) and gap-fill replays ([deliver]) go through the same [deliver] path, so a
 * message that arrived both ways — live just before a drop, then again in the catch-up — is shown
 * once. `Pong` never reaches [incoming]: it is a liveness signal and nothing above this class has a
 * use for it (#802).
 */
internal class InboundPump(private val messageEncryptor: MessageEncryptor?) {

    private val _incoming = MutableSharedFlow<WsMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<WsMessage> = _incoming

    private val dedupLock = Mutex()
    private val processedIds = LinkedHashSet<String>()

    class Outcome(val authRejected: Boolean)

    /**
     * Reads [socket] until it closes. Every frame counts as proof of life on [liveness]. The first
     * frame that is not an authentication refusal calls [onFirstAnswer]: the server has accepted the
     * token. [onFrame] sees each decoded frame before it is published (the outbox needs acks).
     */
    suspend fun read(
        socket: WebSocketSession,
        liveness: SessionLiveness,
        onFirstAnswer: () -> Unit,
        onFrame: suspend (WsMessage) -> Unit
    ): Outcome {
        var answered = false
        var authRejected = false
        runCatchingCancellable {
            for (frame in socket.incoming) {
                liveness.heard()
                if (frame !is Frame.Text) continue
                val decoded = runCatchingCancellable { wsJson.decodeFromString<WsMessage>(frame.readText()) }
                    .onFailure { e -> Log.e(TAG, "Parse error: ${e.message}") }
                    .getOrNull() ?: continue
                if (decoded is WsMessage.Error && decoded.code == AUTH_TOKEN_INVALID) {
                    authRejected = true
                    continue
                }
                if (!answered) {
                    answered = true
                    onFirstAnswer()
                }
                if (decoded == WsMessage.Pong) continue
                onFrame(decoded)
                deliver(decoded)
            }
        }.onFailure { e -> Log.d(TAG, "Socket read ended: ${e.message}") }
        return Outcome(authRejected)
    }

    /** Publishes [message] unless an identical event was already published. */
    suspend fun deliver(message: WsMessage) {
        val key = dedupKey(message)
        if (key != null) {
            val fresh = dedupLock.withLock {
                val added = processedIds.add(key)
                while (processedIds.size > MAX_PROCESSED_IDS) processedIds.remove(processedIds.first())
                added
            }
            if (!fresh) {
                Log.d(TAG, "Skipping duplicate: $key")
                return
            }
        }
        val out = if (message is WsMessage.NewMessage && messageEncryptor != null) {
            runCatchingCancellable { messageEncryptor.decryptIncoming(message) }.getOrDefault(message)
        } else {
            message
        }
        _incoming.emit(out)
    }

    private fun dedupKey(message: WsMessage): String? = when (message) {
        is WsMessage.NewMessage -> message.messageId
        is WsMessage.ServerAck -> message.requestId
        is WsMessage.StatusUpdate -> "${message.messageId}_${message.status}"
        // Suffixed: the set is shared across frame types, and a bare messageId would collide with
        // the NewMessage that delivered the same message.
        is WsMessage.MessageDeleted -> "${message.messageId}_deleted"
        is WsMessage.MessageEdited -> "${message.messageId}_edited"
        is WsMessage.MessageExpired -> "${message.messageId}_expired"
        else -> null
    }

    private companion object {
        const val TAG = "InboundPump"
        const val MAX_PROCESSED_IDS = 500
        const val AUTH_TOKEN_INVALID = "AUTH_TOKEN_INVALID"
    }
}

/**
 * Whether one socket is still answering (#802).
 *
 * The old heartbeat sent a ping every 30 seconds and never waited for the pong, so a half-open
 * socket stayed "connected" while every message written to it vanished. Here a ping always carries
 * a deadline: [watch] returns — meaning "dead" — when a ping, or the probe sent the moment the
 * socket opens, draws no frame at all within the timeout. Any inbound frame counts, so a busy chat
 * needs no pings.
 */
internal class SessionLiveness(private val clock: () -> Long) {

    @Volatile
    private var lastHeardAt = Long.MIN_VALUE

    fun heard() {
        lastHeardAt = clock()
    }

    /** Suspends while [socket] keeps answering; returns once it has gone quiet past its deadline. */
    suspend fun watch(socket: WebSocketSession, pingIntervalMs: Long, pongTimeoutMs: Long) {
        while (true) {
            val pingAt = clock()
            val sent = runCatchingCancellable {
                socket.outgoing.send(Frame.Text(wsJson.encodeToString<WsMessage>(WsMessage.Ping)))
            }
            if (sent.isFailure) return
            delay(pongTimeoutMs)
            if (lastHeardAt < pingAt) return
            // Alive. Stay quiet until the line has been silent for a whole interval.
            while (true) {
                val quietFor = clock() - lastHeardAt
                if (quietFor >= pingIntervalMs) break
                delay(pingIntervalMs - quietFor)
            }
        }
    }
}
