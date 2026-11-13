package com.muhabbet.app.data.remote

import com.muhabbet.app.crypto.MessageEncryptor
import com.muhabbet.app.data.outbox.Outbox
import com.muhabbet.app.data.outbox.OutgoingMessage
import com.muhabbet.app.data.remote.connection.ConnectionEffect
import com.muhabbet.app.data.remote.connection.ConnectionEvent
import com.muhabbet.app.data.remote.connection.ConnectionPolicy
import com.muhabbet.app.data.remote.connection.ConnectionState
import com.muhabbet.app.data.remote.connection.reduce
import com.muhabbet.app.data.sync.MissedMessageCatchUp
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.parameter
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.time.Clock

/**
 * The app's one WebSocket: a connection state machine, an outbox and a de-duplicated inbound stream.
 *
 * **Connection (#811).** [ConnectionState] is a sealed state machine whose whole transition table
 * is the pure [reduce]. One actor coroutine owns it: [connect], [disconnect] and everything the
 * socket reports become events on one channel, the actor reduces them in order and executes the
 * effects. Nothing else writes connection state, so the races between the caller's thread and the
 * loop that the old `generation` / `shouldReconnect` / `connectJob` fields fought (#511, #521) have
 * nowhere to happen.
 *
 * **Liveness (#802).** A socket is only Live once the server has answered on it, and stays Live only
 * while it keeps answering: [InboundPump] pings when the line has been quiet and declares the socket
 * lost if no frame follows within [PONG_TIMEOUT_MS]. A half-open TCP connection — NAT rebinding, a
 * Wi-Fi to LTE handover — used to stay "connected" until the OS gave up, minutes later.
 *
 * **Outbound messages (#792, #795)** go through the durable [Outbox]: persisted whole before they
 * touch the wire, confirmed only by the server's ack, retried under the same messageId, and failed
 * visibly rather than dropped.
 *
 * **Gap-fill (#797).** Every time a socket becomes Live, [MissedMessageCatchUp] fetches what arrived
 * while there was none, and it is replayed through the same path as live frames.
 */
@OptIn(ExperimentalAtomicApi::class)
class WsClient(
    private val apiClient: ApiClient,
    private val tokenProvider: () -> String?,
    /** The outbox. Null only in tests that do not send messages; then sends cannot be accepted. */
    outbox: Outbox? = null,
    private val messageEncryptor: MessageEncryptor? = null,
    /** Supplied by tests so every timer runs on virtual time. */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /** Tests only: how a session is opened (Ktor's MockEngine has no WebSocket capability). */
    private val openSession: (suspend (token: String) -> WebSocketSession)? = null,
    private val catchUp: MissedMessageCatchUp? = null,
    /** Explicit token refresh after the server rejects the socket's token. */
    private val refreshAuth: suspend () -> Unit = { apiClient.refreshAccessToken() },
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val policy: ConnectionPolicy = ConnectionPolicy(jitter = ::spread)
) {

    private val outbox: Outbox? = outbox

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val inbound = InboundPump(messageEncryptor)
    val incoming: SharedFlow<WsMessage> = inbound.incoming

    private val pendingAcks = PendingAckQueue()

    /** Re-asserted on every new session: the server forgets focus when a socket closes (#667). */
    @Volatile
    private var focusedConversationId: String? = null

    /** The live socket, written only by the actor. Read by [send] for frames that are not queued. */
    @Volatile
    private var session: WebSocketSession? = null

    // ─── The actor ───

    private class Envelope(val event: ConnectionEvent, val serial: Long? = null, val socket: WebSocketSession? = null, val generation: Long? = null)

    private val events = Channel<Envelope>(Channel.UNLIMITED)
    private val generation = AtomicLong(0)
    private val actor: Job by lazy { scope.launch { for (envelope in events) handle(envelope) } }

    // Owned by the actor coroutine; nothing else touches these.
    private var liveGeneration = 0L
    private var serial = 0L
    private var sessionJobs: Job? = null
    private var retryJob: Job? = null

    /**
     * Asks for a connection and returns the generation to hand back to [disconnect]. A later
     * [connect] supersedes an earlier one, so a stale composition's teardown is ignored (#511).
     */
    fun connect(): Long {
        val mine = generation.addAndFetch(1L)
        actor.start()
        events.trySend(Envelope(ConnectionEvent.Start, generation = mine))
        return mine
    }

    /** Tears the connection down — only if [generation] is still the one that asked for it. */
    fun disconnect(generation: Long) {
        events.trySend(Envelope(ConnectionEvent.Stop, generation = generation))
    }

    private suspend fun handle(envelope: Envelope) {
        val event = envelope.event
        when {
            event == ConnectionEvent.Start -> liveGeneration = envelope.generation ?: liveGeneration
            event == ConnectionEvent.Stop && envelope.generation != liveGeneration -> {
                Log.d(TAG, "Ignoring stale disconnect #${envelope.generation} (live is #$liveGeneration)")
                return
            }
            // An event from a socket this client has already moved past.
            envelope.serial != null && envelope.serial != serial -> {
                envelope.socket?.let { stale -> scope.launch { runCatchingCancellable { stale.close() } } }
                return
            }
        }
        if (event == ConnectionEvent.Opened) {
            val socket = envelope.socket ?: return
            // Only a socket the client is still waiting for is adopted; anything else is closed
            // rather than left open with nobody reading it.
            if (_connectionState.value !is ConnectionState.Connecting) {
                scope.launch { runCatchingCancellable { socket.close() } }
                return
            }
            adopt(socket)
        }
        val transition = reduce(_connectionState.value, event, policy)
        _connectionState.value = transition.state
        transition.effects.forEach { effect ->
            runCatchingCancellable { execute(effect) }
                .onFailure { e -> Log.e(TAG, "Connection effect $effect failed: ${e.message}") }
        }
    }

    private fun post(event: ConnectionEvent, forSerial: Long, socket: WebSocketSession? = null) {
        events.trySend(Envelope(event, serial = forSerial, socket = socket))
    }

    private suspend fun execute(effect: ConnectionEffect) {
        when (effect) {
            ConnectionEffect.OpenSocket -> openSocket()
            ConnectionEffect.CloseSocket -> closeSocket()
            ConnectionEffect.WatchForToken -> watchForToken()
            is ConnectionEffect.ScheduleRetry -> {
                val mine = serial
                retryJob?.cancel()
                retryJob = scope.launch { delay(effect.delayMs); post(ConnectionEvent.RetryDue, mine) }
            }
            ConnectionEffect.CancelRetry -> { retryJob?.cancel(); retryJob = null }
            ConnectionEffect.SessionLive -> onSessionLive()
            ConnectionEffect.SessionLost -> outbox?.detach()
            ConnectionEffect.RefreshAuth -> scope.launch {
                runCatchingCancellable { refreshAuth() }.onFailure { e -> Log.w(TAG, "Token refresh failed: ${e.message}") }
            }
        }
    }

    private fun openSocket() {
        val mine = ++serial
        sessionJobs?.cancel()
        sessionJobs = scope.launch {
            val token = tokenProvider() ?: return@launch post(ConnectionEvent.TokenMissing, mine)
            runCatchingCancellable {
                openSession?.invoke(token)
                    ?: apiClient.httpClient.webSocketSession("${ApiClient.BASE_URL.replace("https", "wss")}/ws") {
                        parameter("token", token)
                    }
            }.fold(
                onSuccess = { socket -> post(ConnectionEvent.Opened, mine, socket) },
                onFailure = { e ->
                    Log.w(TAG, "Could not open the socket: ${e.message}")
                    post(ConnectionEvent.Lost(clock()), mine)
                }
            )
        }
    }

    /** The actor has accepted [socket] as the current session: read it and watch its liveness. */
    private fun adopt(socket: WebSocketSession) {
        val mine = serial
        session = socket
        val liveness = SessionLiveness(clock)
        sessionJobs = scope.launch {
            launch {
                val outcome = inbound.read(
                    socket = socket,
                    liveness = liveness,
                    onFirstAnswer = { post(ConnectionEvent.ServerAnswered(clock()), mine) },
                    onFrame = ::routeToOutbox
                )
                post(ConnectionEvent.Lost(clock(), authRejected = outcome.authRejected), mine)
            }
            launch {
                liveness.watch(socket, PING_INTERVAL_MS, PONG_TIMEOUT_MS)
                Log.w(TAG, "No answer within ${PONG_TIMEOUT_MS}ms: treating the socket as dead")
                post(ConnectionEvent.Lost(clock()), mine)
            }
        }
    }

    private fun closeSocket() {
        // Retire this connection's serial, so whatever its jobs still post is recognised as stale.
        serial++
        sessionJobs?.cancel()
        sessionJobs = null
        val stale = session ?: return
        session = null
        scope.launch { runCatchingCancellable { stale.close() } }
    }

    /** Re-reads the token locally until one appears. No network: there is nobody to call yet. */
    private fun watchForToken() {
        val mine = serial
        retryJob?.cancel()
        retryJob = scope.launch {
            while (tokenProvider() == null) delay(TOKEN_POLL_MS)
            events.trySend(Envelope(ConnectionEvent.TokenAvailable))
            Log.d(TAG, "Token available (attempt serial $mine)")
        }
    }

    private suspend fun onSessionLive() {
        val socket = session ?: return
        outbox?.attach { frame -> socket.outgoing.send(Frame.Text(wsJson.encodeToString<WsMessage>(frame))) }
        scope.launch {
            outbox?.drain()
            drainPendingAcks()
            reassertConversationFocus()
            catchUp?.missed()?.forEach { inbound.deliver(it) }
        }
    }

    private suspend fun routeToOutbox(frame: WsMessage) {
        catchUp?.observe(frame)
        when (frame) {
            is WsMessage.ServerAck -> outbox?.onServerAck(frame)
            is WsMessage.StatusUpdate -> outbox?.onStatusUpdate(frame)
            else -> Unit
        }
    }

    // ─── Sending ───

    /**
     * Sends [message]. A `message.send` goes to the [Outbox]: it returns as soon as the frame is
     * persisted, whether or not a socket is up, and its progress is on [outboxFor]. It throws
     * [MessageCouldNotBeQueuedException] only if it could not be persisted — then nothing will send
     * it. Any other frame is written to the live socket or fails with [NotConnectedException].
     */
    suspend fun send(message: WsMessage) {
        val outgoing = encryptForWire(message)
        if (outgoing is WsMessage.SendMessage) {
            if (outbox?.enqueue(outgoing) != true) throw MessageCouldNotBeQueuedException()
            return
        }
        val live = session ?: throw NotConnectedException()
        live.outgoing.send(Frame.Text(wsJson.encodeToString<WsMessage>(outgoing)))
    }

    /** The outbox of one conversation, for the chat to merge (Queued / Sending / Failed / …). */
    fun outboxFor(conversationId: String): Flow<List<OutgoingMessage>> =
        outbox?.forConversation(conversationId) ?: MutableStateFlow(emptyList())

    /** Re-queues a failed message under its original messageId. */
    suspend fun retry(messageId: String) {
        outbox?.retry(messageId)
    }

    /**
     * Sends a delivery receipt, queueing it for the next connection instead of throwing. Returns
     * whether it went out now. Cancellation still propagates (#478).
     */
    suspend fun sendAck(ack: WsMessage.AckMessage): Boolean {
        val live = session ?: run { pendingAcks.record(ack); return false }
        return runCatchingCancellable {
            live.outgoing.send(Frame.Text(wsJson.encodeToString<WsMessage>(ack)))
        }.fold(
            onSuccess = { true },
            onFailure = { e ->
                Log.w(TAG, "Delivery receipt for ${ack.messageId} queued: ${e.message}")
                pendingAcks.record(ack)
                false
            }
        )
    }

    /**
     * Reports which conversation is on screen and remembers it, so every new session restates it —
     * the server's copy dies with the socket (#667). Best-effort on the wire.
     */
    suspend fun setConversationFocus(conversationId: String?) {
        focusedConversationId = conversationId
        sendIfConnected(WsMessage.ConversationFocus(conversationId))
    }

    private suspend fun reassertConversationFocus() {
        val focused = focusedConversationId ?: return
        sendIfConnected(WsMessage.ConversationFocus(focused))
    }

    private suspend fun sendIfConnected(frame: WsMessage) {
        val live = session ?: return
        runCatchingCancellable { live.outgoing.send(Frame.Text(wsJson.encodeToString(frame))) }
            .onFailure { e -> Log.d(TAG, "Frame not sent (${frame::class.simpleName}): ${e.message}") }
    }

    /** E2E encrypt-on-send seam, gated inside [MessageEncryptor] by `E2EConfig.ENABLED`. */
    private suspend fun encryptForWire(message: WsMessage): WsMessage =
        if (message is WsMessage.SendMessage && messageEncryptor != null) {
            messageEncryptor.encryptOutgoing(message)
        } else {
            message
        }

    /** Replays receipts that missed the wire; stops at the first failure and keeps the rest. */
    private suspend fun drainPendingAcks() {
        val acks = pendingAcks.takeAll()
        acks.forEachIndexed { index, ack ->
            if (!sendAck(ack)) {
                acks.drop(index + 1).forEach { pendingAcks.record(it) }
                return
            }
        }
    }

    internal companion object {
        private const val TAG = "WsClient"

        /** How long the line may be quiet before a ping is sent. */
        const val PING_INTERVAL_MS = 25_000L

        /** How long a ping (or the probe on a new socket) may go unanswered before the socket is dead. */
        const val PONG_TIMEOUT_MS = 10_000L

        private const val TOKEN_POLL_MS = 2_000L

        /** ±25% so a server restart is not met by every client at the same instant. */
        private fun spread(delayMs: Long): Long =
            delayMs + (delayMs * 0.25 * (Random.nextDouble() * 2 - 1)).toLong()
    }
}

/**
 * Thrown by [WsClient.send] when a message could not be persisted to the outbox, so nothing will
 * send it later: the optimistic bubble must go and the user must be told.
 */
open class MessageNotQueuedException(message: String) : Exception(message)

/** The outbox could not accept the message: no store, or the write itself failed. */
class MessageCouldNotBeQueuedException : MessageNotQueuedException("Message could not be queued")

/** A frame that is not a message (typing, call signalling) was sent with no live socket. */
class NotConnectedException : Exception("WebSocket not connected")
