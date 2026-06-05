package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.LocalCache
import com.muhabbet.app.data.local.PendingMessageData
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.muhabbet.app.util.Log
import kotlinx.serialization.encodeToString
import kotlin.random.Random

class WsClient(
    private val apiClient: ApiClient,
    private val tokenProvider: () -> String?,
    private val localCache: LocalCache? = null,
    // E2E encrypt-on-send / decrypt-on-receive. Null = no encryption layer (legacy pass-through).
    // Even when non-null, behavior is gated internally by E2EConfig.ENABLED (default OFF).
    private val messageEncryptor: com.muhabbet.app.crypto.MessageEncryptor? = null
) {

    companion object {
        private const val TAG = "WsClient"
        private const val MAX_RETRY_COUNT = 5
    }

    private var session: WebSocketSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _incoming = MutableSharedFlow<WsMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<WsMessage> = _incoming

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Deduplication: track recently processed message IDs
    private val processedMessageIds = LinkedHashSet<String>()
    private val maxProcessedIds = 500

    private var reconnectAttempt = 0
    private var shouldReconnect = true
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    fun connect() {
        scope.launch {
            shouldReconnect = true
            _connectionState.value = ConnectionState.CONNECTING
            connectInternal()
        }
    }

    private suspend fun connectInternal() {
        while (shouldReconnect) {
            val token = tokenProvider()
            if (token == null) {
                Log.w(TAG, "No token available, waiting...")
                _connectionState.value = ConnectionState.WAITING_FOR_AUTH
                delay(2000)
                continue
            }
            try {
                _connectionState.value = ConnectionState.CONNECTING
                Log.d(TAG, "Connecting...")
                session = apiClient.httpClient.webSocketSession("${ApiClient.BASE_URL.replace("https", "wss")}/ws") {
                    parameter("token", token)
                }
                reconnectAttempt = 0
                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "Connected")

                // Drain pending messages on successful reconnect
                drainPendingMessages()

                // Start heartbeat
                heartbeatJob = scope.launch {
                    while (isActive) {
                        delay(30_000L)
                        try {
                            send(WsMessage.Ping)
                        } catch (_: Exception) { }
                    }
                }

                session?.let { ws ->
                    for (frame in ws.incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            try {
                                val decoded = wsJson.decodeFromString<WsMessage>(text)
                                // Dedup: skip already-processed messages
                                val msgId = extractMessageId(decoded)
                                if (msgId != null && !processedMessageIds.add(msgId)) {
                                    Log.d(TAG, "Skipping duplicate message: $msgId")
                                    continue
                                }
                                trimProcessedIds()
                                // E2E decrypt-on-receive: NewMessage bodies may be encrypted
                                // envelopes; everything else passes through untouched.
                                val message = if (decoded is WsMessage.NewMessage && messageEncryptor != null) {
                                    messageEncryptor.decryptIncoming(decoded)
                                } else {
                                    decoded
                                }
                                _incoming.emit(message)
                            } catch (e: Exception) {
                                Log.e(TAG, "Parse error: ${e.message}")
                            }
                        }
                    }
                }

                // Stop heartbeat on disconnect
                heartbeatJob?.cancel()
                heartbeatJob = null
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.d(TAG, "Session closed, will reconnect")
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.e(TAG, "Connection error: ${e.message}")
            }

            session = null
            if (shouldReconnect) {
                reconnectAttempt++
                val baseBackoff = minOf(1000L * (1L shl minOf(reconnectAttempt, 5)), 30_000L)
                // Add jitter: ±25% randomization to prevent thundering herd
                val jitter = (baseBackoff * 0.25 * (Random.nextDouble() * 2 - 1)).toLong()
                val backoff = baseBackoff + jitter
                _connectionState.value = ConnectionState.RECONNECTING
                Log.d(TAG, "Reconnecting in ${backoff}ms (attempt $reconnectAttempt)")
                delay(backoff)
                // Trigger Ktor Auth token refresh via a lightweight REST call
                try {
                    apiClient.httpClient.get("${ApiClient.BASE_URL}/api/v1/users/me")
                    Log.d(TAG, "Token refresh check OK")
                } catch (e: Exception) {
                    Log.d(TAG, "Token refresh check failed: ${e.message}")
                }
            }
        }
    }

    suspend fun send(message: WsMessage) {
        // E2E encrypt-on-send happens inside encryptForWire(); gated by E2EConfig.ENABLED
        // inside MessageEncryptor (no-op + original returned when disabled or not eligible).
        val outgoing = encryptForWire(message)
        val currentSession = session
        if (currentSession == null) {
            // Queue message for later delivery if we have a cache.
            // NOTE: the queued body is the already-encrypted `outgoing`; the drain path is
            // idempotent and will not re-wrap it (encryptOutgoing skips existing envelopes).
            queuePendingMessage(outgoing)
            throw Exception("WebSocket not connected")
        }
        currentSession.outgoing.send(Frame.Text(wsJson.encodeToString(outgoing)))
    }

    /**
     * Send with offline queue fallback — does NOT throw if disconnected.
     * Returns true if sent immediately, false if queued.
     */
    suspend fun sendOrQueue(message: WsMessage): Boolean {
        // Encrypt-on-send before either transmitting or queueing, so the offline path never
        // stores/sends a plaintext body when E2E is enabled (mirrors send()/drainPendingMessages()).
        val outgoing = encryptForWire(message)
        return try {
            val currentSession = session ?: run {
                queuePendingMessage(outgoing)
                return false
            }
            currentSession.outgoing.send(Frame.Text(wsJson.encodeToString(outgoing)))
            true
        } catch (e: Exception) {
            queuePendingMessage(outgoing)
            false
        }
    }

    /**
     * Single E2E encrypt-on-send seam shared by every path that puts a [WsMessage] on the wire
     * ([send], [sendOrQueue], [drainPendingMessages]). Only [WsMessage.SendMessage] bodies are
     * touched; everything else is returned as-is. A no-op (returns the original) when no encryptor
     * is wired or [com.muhabbet.app.crypto.E2EConfig.ENABLED] is false, and idempotent for bodies
     * that are already an [com.muhabbet.shared.port.E2EEnvelope] (safe to re-run on queue resend).
     */
    private suspend fun encryptForWire(message: WsMessage): WsMessage =
        if (message is WsMessage.SendMessage && messageEncryptor != null) {
            messageEncryptor.encryptOutgoing(message)
        } else {
            message
        }

    fun disconnect() {
        shouldReconnect = false
        _connectionState.value = ConnectionState.DISCONNECTED
        scope.launch {
            heartbeatJob?.cancel()
            heartbeatJob = null
            session?.close()
            session = null
        }
    }

    // --- Offline Queue ---

    private fun queuePendingMessage(message: WsMessage) {
        val cache = localCache ?: return
        val sendMessage = message as? WsMessage.SendMessage ?: return
        try {
            cache.insertPendingMessage(
                PendingMessageData(
                    id = sendMessage.requestId,
                    messageId = sendMessage.messageId,
                    conversationId = sendMessage.conversationId,
                    contentType = sendMessage.contentType.name,
                    content = sendMessage.content,
                    replyToId = sendMessage.replyToId,
                    mediaUrl = sendMessage.mediaUrl,
                    clientTimestamp = Clock.System.now().toString()
                )
            )
            Log.d(TAG, "Queued pending message: ${sendMessage.requestId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue pending message: ${e.message}")
        }
    }

    private suspend fun drainPendingMessages() {
        val cache = localCache ?: return
        val pending = cache.getPendingMessages()
        if (pending.isEmpty()) return
        Log.d(TAG, "Draining ${pending.size} pending messages")
        for (msg in pending) {
            if (msg.retryCount >= MAX_RETRY_COUNT) {
                Log.w(TAG, "Dropping pending message ${msg.id} after $MAX_RETRY_COUNT retries")
                cache.deletePendingMessage(msg.id)
                continue
            }
            try {
                val contentType = try {
                    com.muhabbet.shared.model.ContentType.valueOf(msg.contentType)
                } catch (_: Exception) {
                    com.muhabbet.shared.model.ContentType.TEXT
                }
                val wsMessage = WsMessage.SendMessage(
                    conversationId = msg.conversationId,
                    contentType = contentType,
                    content = msg.content,
                    replyToId = msg.replyToId,
                    mediaUrl = msg.mediaUrl,
                    requestId = msg.id,
                    messageId = msg.messageId
                )
                // Encrypt-on-send for the offline drain too. Idempotent: a body queued by send()
                // is already an envelope and is passed through unchanged; a body queued by
                // sendOrQueue() is also pre-encrypted. Plaintext bodies (E2E off / not eligible)
                // pass through untouched, so this is byte-identical to legacy when the flag is OFF.
                val outgoing = encryptForWire(wsMessage)
                val json = wsJson.encodeToString(outgoing)
                session?.outgoing?.send(Frame.Text(json))
                cache.deletePendingMessage(msg.id)
                Log.d(TAG, "Sent pending message: ${msg.id}")
            } catch (e: Exception) {
                cache.incrementRetryCount(msg.id)
                Log.e(TAG, "Failed to send pending message ${msg.id}: ${e.message}")
                break // Stop draining on first failure
            }
        }
    }

    // --- Deduplication ---

    private fun extractMessageId(message: WsMessage): String? {
        return when (message) {
            is WsMessage.NewMessage -> message.messageId
            is WsMessage.ServerAck -> message.requestId
            is WsMessage.StatusUpdate -> "${message.messageId}_${message.status}"
            is WsMessage.MessageDeleted -> message.messageId
            is WsMessage.MessageEdited -> "${message.messageId}_edited"
            else -> null // Don't dedup typing, presence, pong etc.
        }
    }

    private fun trimProcessedIds() {
        while (processedMessageIds.size > maxProcessedIds) {
            processedMessageIds.iterator().let {
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
        }
    }
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    WAITING_FOR_AUTH
}
