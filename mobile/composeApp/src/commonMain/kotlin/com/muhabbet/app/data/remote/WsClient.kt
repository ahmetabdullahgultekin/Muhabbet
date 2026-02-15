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
    private val localCache: LocalCache? = null
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
                                val message = wsJson.decodeFromString<WsMessage>(text)
                                // Dedup: skip already-processed messages
                                val msgId = extractMessageId(message)
                                if (msgId != null && !processedMessageIds.add(msgId)) {
                                    Log.d(TAG, "Skipping duplicate message: $msgId")
                                    continue
                                }
                                trimProcessedIds()
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
        val currentSession = session
        if (currentSession == null) {
            // Queue message for later delivery if we have a cache
            queuePendingMessage(message)
            throw Exception("WebSocket not connected")
        }
        val json = wsJson.encodeToString(message)
        currentSession.outgoing.send(Frame.Text(json))
    }

    /**
     * Send with offline queue fallback — does NOT throw if disconnected.
     * Returns true if sent immediately, false if queued.
     */
    suspend fun sendOrQueue(message: WsMessage): Boolean {
        return try {
            val currentSession = session ?: run {
                queuePendingMessage(message)
                return false
            }
            val json = wsJson.encodeToString(message)
            currentSession.outgoing.send(Frame.Text(json))
            true
        } catch (e: Exception) {
            queuePendingMessage(message)
            false
        }
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
                    messageId = msg.id
                )
                val json = wsJson.encodeToString<WsMessage>(wsMessage)
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
