package com.muhabbet.app.data.remote

import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.parameter
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

class WsClient(private val apiClient: ApiClient) {

    private var session: WebSocketSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _incoming = MutableSharedFlow<WsMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<WsMessage> = _incoming

    private var reconnectAttempt = 0
    private var shouldReconnect = true

    fun connect(token: String) {
        scope.launch {
            shouldReconnect = true
            connectInternal(token)
        }
    }

    private suspend fun connectInternal(token: String) {
        while (shouldReconnect) {
            try {
                session = apiClient.httpClient.webSocketSession("${ApiClient.BASE_URL.replace("https", "wss")}/ws") {
                    parameter("token", token)
                }
                reconnectAttempt = 0

                session?.let { ws ->
                    for (frame in ws.incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            try {
                                val message = wsJson.decodeFromString<WsMessage>(text)
                                _incoming.emit(message)
                            } catch (_: Exception) { }
                        }
                    }
                }
            } catch (_: Exception) { }

            if (shouldReconnect) {
                reconnectAttempt++
                val backoff = minOf(1000L * (1L shl minOf(reconnectAttempt, 5)), 30_000L)
                delay(backoff)
            }
        }
    }

    suspend fun send(message: WsMessage) {
        val json = wsJson.encodeToString(message)
        session?.outgoing?.send(Frame.Text(json))
    }

    fun disconnect() {
        shouldReconnect = false
        scope.launch {
            session?.close()
            session = null
        }
    }
}
