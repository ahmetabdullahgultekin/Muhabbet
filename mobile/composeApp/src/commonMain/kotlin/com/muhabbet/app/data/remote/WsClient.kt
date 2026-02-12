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

class WsClient(private val apiClient: ApiClient, private val tokenProvider: () -> String?) {

    private var session: WebSocketSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _incoming = MutableSharedFlow<WsMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<WsMessage> = _incoming

    private var reconnectAttempt = 0
    private var shouldReconnect = true
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    fun connect() {
        scope.launch {
            shouldReconnect = true
            connectInternal()
        }
    }

    private suspend fun connectInternal() {
        while (shouldReconnect) {
            val token = tokenProvider()
            if (token == null) {
                println("MUHABBET WS: No token available, waiting...")
                delay(2000)
                continue
            }
            try {
                println("MUHABBET WS: Connecting...")
                session = apiClient.httpClient.webSocketSession("${ApiClient.BASE_URL.replace("https", "wss")}/ws") {
                    parameter("token", token)
                }
                reconnectAttempt = 0
                println("MUHABBET WS: Connected!")

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
                                _incoming.emit(message)
                            } catch (e: Exception) {
                                println("MUHABBET WS: Parse error: ${e.message}")
                            }
                        }
                    }
                }

                // Stop heartbeat on disconnect
                heartbeatJob?.cancel()
                heartbeatJob = null
                println("MUHABBET WS: Session closed, will reconnect")
            } catch (e: Exception) {
                println("MUHABBET WS: Connection error: ${e.message}")
            }

            session = null
            if (shouldReconnect) {
                reconnectAttempt++
                val backoff = minOf(1000L * (1L shl minOf(reconnectAttempt, 5)), 30_000L)
                println("MUHABBET WS: Reconnecting in ${backoff}ms (attempt $reconnectAttempt)")
                delay(backoff)
            }
        }
    }

    suspend fun send(message: WsMessage) {
        val currentSession = session
            ?: throw Exception("WebSocket not connected")
        val json = wsJson.encodeToString(message)
        currentSession.outgoing.send(Frame.Text(json))
    }

    fun disconnect() {
        shouldReconnect = false
        scope.launch {
            session?.close()
            session = null
        }
    }
}
