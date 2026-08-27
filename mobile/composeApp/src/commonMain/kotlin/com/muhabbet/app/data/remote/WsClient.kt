package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.PendingMessageCache
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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import kotlinx.serialization.encodeToString
import kotlin.random.Random

class WsClient(
    private val apiClient: ApiClient,
    private val tokenProvider: () -> String?,
    // The interface, not LocalCache: the concrete class needs a SQLDelight driver and an Android
    // Context, so naming it here meant the queue path could only be tested with `null` — which
    // is exactly how a test came to assert that a message had been queued by a client that had
    // nowhere to queue it. Production still passes LocalCache, which implements this.
    private val localCache: PendingMessageCache? = null,
    // E2E encrypt-on-send / decrypt-on-receive. Null = no encryption layer (legacy pass-through).
    // Even when non-null, behavior is gated internally by E2EConfig.ENABLED (default OFF).
    private val messageEncryptor: com.muhabbet.app.crypto.MessageEncryptor? = null,
    // Supplied only by tests, so the reconnect loop, the heartbeat and the watchdog can be driven
    // on virtual time instead of waiting 30 real seconds per tick. Production passes nothing.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    // Also tests only: how a session is opened. Ktor's MockEngine does not implement the WebSocket
    // capability, so without this seam nothing the client puts on the wire when a session comes up
    // — the two drains, and now the focus re-assert — could be observed at all. Production passes
    // nothing and gets the real Ktor session.
    private val openSession: (suspend (token: String) -> WebSocketSession)? = null
) {

    companion object {
        private const val TAG = "WsClient"
        private const val MAX_RETRY_COUNT = 5

        /**
         * How often the heartbeat pings, and therefore how long the watchdog can take to notice
         * that the client is down with nothing running. Both jobs are the same coroutine on
         * purpose — see [startHeartbeat].
         */
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }

    private var session: WebSocketSession? = null

    private val _incoming = MutableSharedFlow<WsMessage>(extraBufferCapacity = 64)
    val incoming: SharedFlow<WsMessage> = _incoming

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // Deduplication: track recently processed message IDs
    private val processedMessageIds = LinkedHashSet<String>()
    private val maxProcessedIds = 500

    // Delivery receipts that missed the wire, replayed by drainPendingAcks() on the next connect.
    private val pendingAcks = PendingAckQueue()

    private var reconnectAttempt = 0
    private var shouldReconnect = false
    private var heartbeatJob: Job? = null

    /**
     * The conversation this client last told the server it is foregrounded on, or null for none.
     *
     * Held here, not only in the screen that reported it, because the server's copy does not
     * survive the socket: `WebSocketSessionManager.forget` clears `activeConversation` once the
     * user's last session goes away, which is correct — with the socket gone it genuinely cannot
     * know any more. The client only ever sent a `ConversationFocus` frame when the focused
     * conversation *changed*, so after a reconnect with the same chat still on screen the server
     * believed the user was looking at nothing and pushed a notification for the conversation they
     * were reading (#667). Remembering it is what lets [reassertConversationFocus] rebuild the
     * server's view instead of assuming it survived.
     */
    private var focusedConversationId: String? = null

    /** The one connect loop, held so a second [connect] or the watchdog cannot start a rival. */
    private var connectJob: Job? = null

    /**
     * Which [connect] the client is currently serving.
     *
     * Handed out by [connect] and handed back to [disconnect], so a teardown can tell whether it
     * belongs to the live connection or to one that has already been replaced. This is the same
     * identity discipline the connect loop's `finally` applies to `session` and `heartbeatJob`,
     * lifted to cover the whole teardown — see [disconnect] for why the loop-local checks are not
     * enough on their own.
     */
    private var generation = 0L

    /**
     * Brings the socket up and keeps it up until the returned generation is passed to [disconnect].
     *
     * `shouldReconnect` is set **here, synchronously**, not inside the coroutine it used to live in.
     * `SessionLifecycle` calls disconnect()+connect() on every composition swap, and an Activity
     * recreation — which the language switch performs deliberately — interleaved them so that the
     * old Activity's `onDispose` wrote `false` after the new loop had already read `true`. The new
     * loop fell out of its own `while (shouldReconnect)`, nothing was left running, and the socket
     * stayed down for as long as the app stayed open while messages queued silently (#511).
     */
    fun connect(): Long {
        val myGeneration = ++generation
        shouldReconnect = true
        reconnectAttempt = 0
        // Only claim CONNECTING when a loop actually has to be started. This looks redundant and
        // is not: [startConnectLoop] is single-flighted, so on an Activity recreation — the common
        // case, and the one the generation guard exists to make survivable — it returns immediately
        // because a healthy loop is already running. That loop is parked in its `incoming` read and
        // will never revisit the line that sets CONNECTED, so overwriting the state here left the
        // app showing "No connection" for as long as it stayed open, while the socket kept
        // delivering messages normally (#521).
        //
        // The invariant this restores: the connect loop is the only writer of [connectionState],
        // apart from [disconnect].
        if (connectJob?.isActive != true) {
            _connectionState.value = ConnectionState.CONNECTING
        }
        startConnectLoop()
        startHeartbeat(myGeneration)
        return myGeneration
    }

    /**
     * Visible for tests only: the reconnect loop, so a test can end it the way a crash would and
     * assert that the watchdog notices. Nothing in production reads this.
     */
    internal val connectLoopForTest: Job? get() = connectJob

    /** Visible for tests only: whether the client still intends to hold a connection open. */
    internal val shouldReconnectForTest: Boolean get() = shouldReconnect

    /**
     * Starts the reconnect loop, unless one is already running.
     *
     * Single-flighted on purpose. `connect()` has historically had no re-entry guard, which is what
     * the connect loop's `finally` comment is defending against, and the watchdog in
     * [startHeartbeat] would otherwise start a fresh loop every 30 seconds forever. Two live loops
     * overwrite each other's `session` and `heartbeatJob`, so the cheapest fix is to never have two.
     *
     * Started lazily and only then handed to `invokeOnCompletion`, so a loop that finishes
     * immediately cannot run its completion handler before `connectJob` has been assigned.
     */
    private fun startConnectLoop() {
        // `isCompleted == false`, not `isActive == true`. The job below is created LAZY, and a lazy
        // job that has not been started yet reports `isActive == false` while being very much
        // alive — so between `connectJob = job` and `job.start()` the old guard waved a second
        // caller straight through, and two connect loops then overwrote each other's `session` and
        // `heartbeatJob`. Exactly the failure this guard exists to prevent, in the one window it
        // could not see. A New job has `isCompleted == false`, so this covers it.
        if (connectJob?.isCompleted == false) return
        val job = scope.launch(start = CoroutineStart.LAZY) { connectInternal() }
        connectJob = job
        // Clear by identity, never by field: a later start may already have installed its own.
        job.invokeOnCompletion { if (connectJob === job) connectJob = null }
        job.start()
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
            // Everything this iteration owns is held in locals as well as in the shared fields.
            // `startConnectLoop()` single-flights the loop now, but `disconnect()` still closes
            // asynchronously, so the cleanup below must only ever touch what *this* iteration
            // created.
            var mySession: WebSocketSession? = null
            try {
                _connectionState.value = ConnectionState.CONNECTING
                Log.d(TAG, "Connecting...")
                val ws = openSession?.invoke(token)
                    ?: apiClient.httpClient.webSocketSession("${ApiClient.BASE_URL.replace("https", "wss")}/ws") {
                        parameter("token", token)
                    }
                mySession = ws
                session = ws
                reconnectAttempt = 0
                _connectionState.value = ConnectionState.CONNECTED
                Log.d(TAG, "Connected")

                // Drain pending messages on successful reconnect
                drainPendingMessages()
                // …and the delivery receipts that could not be sent while it was down. Receipts are
                // drained second on purpose: a queued message may be the very thing a queued receipt
                // refers to on the other side, and there is no reason to make the server reconcile
                // them out of order.
                drainPendingAcks()
                // …and the claim about which chat is on screen, which the server dropped when the
                // old socket went away. Last, because it is the only one of the three whose absence
                // costs a notification rather than a message (#667).
                reassertConversationFocus()

                // No heartbeat is started here any more. It used to be created per iteration and
                // cancelled in the `finally` below, which meant every path that skipped the cancel
                // orphaned a coroutine that went on pinging the *next* session — and, once the
                // reconnect loop died, went on pinging nothing at all, which is the "ping every 30s
                // with no loop behind it" in #511. There is now exactly one heartbeat per
                // `connect()`, owned by the client rather than by an iteration; see
                // [startHeartbeat].

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

                _connectionState.value = ConnectionState.DISCONNECTED
                Log.d(TAG, "Session closed, will reconnect")
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.e(TAG, "Connection error: ${e.message}")
            } finally {
                // Clear by identity, not by field. `WsClient` is a Koin single and
                // `SessionLifecycle` calls disconnect()+connect() on every composition swap (every
                // Activity recreation, including the deliberate language switch), so a later
                // iteration may already have overwritten `session` by the time this one's socket
                // closes. Nulling the field blindly would drop a live session on the floor; it is
                // only cleared while it still holds *this* iteration's object.
                if (session === mySession) session = null
            }

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
            // A view-once message is never queued, because the queue cannot carry the flag.
            //
            // `pendingMessage` stores id, messageId, conversationId, contentType, content,
            // replyToId, mediaUrl and a timestamp — there is no `viewOnce` column, so
            // drainPendingMessages() rebuilds the frame with `viewOnce` at its default of false.
            // Queueing a sealed photo therefore delivered it on the next reconnect as an ordinary,
            // permanent one: #515 all over again, on the path where the user is least likely to be
            // watching. Failing the send is the honest outcome — the alternative is a promise
            // silently downgraded minutes later.
            //
            // Fixing it properly means a SQLDelight migration adding `viewOnce` (and, while there,
            // the `thumbnailUrl` and `forwardedFrom` this queue also drops). That is a schema
            // version bump on an on-device database with no emulator here to verify the upgrade
            // path against, so it is deliberately not bundled into a privacy fix.
            if (message is WsMessage.SendMessage && message.viewOnce) {
                throw ViewOnceNotQueueableException()
            }
            // Queue message for later delivery if we have a cache.
            // NOTE: the queued body is the already-encrypted `outgoing`; the drain path is
            // idempotent and will not re-wrap it (encryptOutgoing skips existing envelopes).
            //
            // The result is checked. Throwing MessageQueuedException unconditionally is what made a
            // message that was never stored look, to the user, exactly like one that was waiting to
            // go — a pending clock that would never resolve, on a message that no longer existed.
            if (!queuePendingMessage(outgoing)) throw MessageCouldNotBeQueuedException()
            throw MessageQueuedException()
        }
        currentSession.outgoing.send(Frame.Text(wsJson.encodeToString(outgoing)))
    }

    /**
     * Sends a delivery receipt, queueing it for the next reconnect instead of throwing.
     *
     * Returns `true` if it went out now, `false` if it was queued. Callers do not have to handle the
     * failure: a receipt is not something to tell the user about, and the previous contract — throw,
     * log "best-effort, re-sent on the next incoming message", and never re-send it if no further
     * message arrived — is the reason ticks stayed wrong until an app restart (#478).
     *
     * Cancellation still propagates. A collector being torn down must not have its own teardown
     * recorded as a socket failure and replayed on the next connect.
     */
    suspend fun sendAck(ack: WsMessage.AckMessage): Boolean {
        val currentSession = session
        if (currentSession == null) {
            pendingAcks.record(ack)
            return false
        }
        return runCatchingCancellable {
            // Encoded as the sealed base, not the concrete subclass: the `type` discriminator
            // wsJson relies on is only emitted by the polymorphic serializer.
            currentSession.outgoing.send(Frame.Text(wsJson.encodeToString<WsMessage>(ack)))
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
     * Reports which conversation is on screen, and remembers it so the claim can be made again.
     *
     * Every focus report goes through here rather than through [sendOrQueue] directly, because a
     * frame that is only sent is a frame the server forgets at the next disconnect. Best-effort on
     * the wire, exactly as before: a lost frame costs one push that was not needed, never a message.
     */
    suspend fun setConversationFocus(conversationId: String?) {
        focusedConversationId = conversationId
        sendOrQueue(WsMessage.ConversationFocus(conversationId))
    }

    /**
     * Restates the focused conversation on a freshly established session.
     *
     * Only when there is one. A newly opened socket has no `activeConversation` entry on the server
     * — `forget` removed it when the previous socket closed — so "none" is already the server's
     * view and sending it again would say nothing.
     *
     * Visible for tests, which drive it through the connect loop rather than calling it directly.
     */
    internal suspend fun reassertConversationFocus() {
        val focused = focusedConversationId ?: return
        sendOrQueue(WsMessage.ConversationFocus(focused))
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

    /**
     * The client's single heartbeat, and its watchdog.
     *
     * One coroutine per [connect], not one per connection attempt, for two reasons. It cannot be
     * orphaned by an iteration that exits without cancelling it, which is half of #511. And it
     * outlives the connect loop, which is what lets it act as a watchdog: if the loop dies for a
     * reason the loop itself cannot catch — a `Throwable` that is not an `Exception`, or a
     * `connect()` that arrived while the previous loop was already on its way out and so declined
     * to start a new one — something has to notice that the client wants a connection and has
     * nothing running. Every 30 seconds, this does.
     *
     * The restart goes through [startConnectLoop], which single-flights, so the watchdog cannot
     * spawn rival loops however often it fires.
     */
    private fun startHeartbeat(myGeneration: Long) {
        // Any heartbeat still in the field belongs to a generation this call has just superseded,
        // because `connect()` bumps the generation immediately before calling us.
        heartbeatJob?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                // A newer connect() owns the client now, or it has been torn down. Either way this
                // generation is done; leave without touching anything the new one installed.
                if (generation != myGeneration || !shouldReconnect) return@launch
                val live = session
                if (live != null) {
                    // Deliberately does not break the loop or surface to the UI: recovery is owned
                    // by the reconnect path. Logged so a heartbeat that keeps failing is visible.
                    // Cancellation is the exception: it must end the loop, not be logged as a
                    // failed ping.
                    runCatchingCancellable { send(WsMessage.Ping) }
                        .onFailure { e -> Log.w(TAG, "Heartbeat ping failed: ${e.message}") }
                } else if (connectJob?.isActive != true) {
                    Log.w(TAG, "Watchdog: disconnected with no connect loop running, restarting it")
                    startConnectLoop()
                }
            }
        }
        heartbeatJob = job
        job.invokeOnCompletion { if (heartbeatJob === job) heartbeatJob = null }
        job.start()
    }

    /**
     * Tears down the generation named by [generation], and does nothing if it is not the live one.
     *
     * The guard is the other half of the #511 fix, and setting `shouldReconnect` synchronously in
     * [connect] does not remove the need for it. `SessionLifecycle`'s `onDispose` belongs to a
     * composition that is already gone, but it still runs on the calling thread whenever Android
     * gets round to it — after the replacement Activity has composed and called `connect()`. An
     * unguarded `disconnect()` at that point sets `shouldReconnect = false` and closes the socket
     * that the *new* composition just opened, which is precisely how the app ended up connected to
     * nothing with no loop running. Passing the generation back makes the teardown idempotent in
     * both interleavings: whichever of the two runs second, only the live generation is torn down.
     *
     * Fields are captured synchronously for the same reason the connect loop's `finally` does it.
     * Only `close()` has to happen off-thread; reading the fields inside the coroutine would mean
     * reading them after a following `connect()` had installed a new session.
     */
    fun disconnect(generation: Long) {
        if (generation != this.generation) {
            Log.d(TAG, "Ignoring stale disconnect for #$generation (live is #${this.generation})")
            return
        }
        shouldReconnect = false
        _connectionState.value = ConnectionState.DISCONNECTED
        val staleSession = session
        val staleHeartbeat = heartbeatJob
        staleHeartbeat?.cancel()
        if (heartbeatJob === staleHeartbeat) heartbeatJob = null
        if (session === staleSession) session = null
        scope.launch {
            staleSession?.close()
        }
    }

    // --- Offline Queue ---

    /**
     * Returns whether the message was actually written to the queue.
     *
     * It used to return `Unit` and had three ways to do nothing — no local cache, a frame that is
     * not a [WsMessage.SendMessage], or an insert that threw — and [send] threw
     * `MessageQueuedException` regardless. So a message that was never stored anywhere was reported
     * to the user as waiting to be sent, and it was simply gone. The caller has to be able to tell
     * the difference, which means this has to say.
     */
    private fun queuePendingMessage(message: WsMessage): Boolean {
        val cache = localCache ?: run {
            Log.w(TAG, "No local cache: message not queued and will not be sent later")
            return false
        }
        val sendMessage = message as? WsMessage.SendMessage ?: run {
            // Not a failure worth alarming about — receipts and typing frames are not queueable and
            // are not meant to be. Only send() treats a false here as something the user must know.
            Log.d(TAG, "Not a SendMessage; nothing queued")
            return false
        }
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
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to queue pending message: ${sendMessage.requestId}", e)
            return false
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

    /**
     * Replays the receipts that could not be sent while the socket was down.
     *
     * Stops at the first failure like [drainPendingMessages] does, and puts everything it has not
     * attempted back on the queue — [sendAck] has already re-queued the one that failed. Dropping
     * the tail would lose exactly the receipts a flapping connection produces most of.
     */
    private suspend fun drainPendingAcks() {
        val acks = pendingAcks.takeAll()
        if (acks.isEmpty()) return
        Log.d(TAG, "Draining ${acks.size} pending delivery receipts")
        acks.forEachIndexed { index, ack ->
            if (!sendAck(ack)) {
                acks.drop(index + 1).forEach { pendingAcks.record(it) }
                return
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
            // Suffixed like `_edited` above, and for the reason `MessageDeleted` should have been:
            // this set is shared across frame types, so a bare messageId here would collide with
            // the `NewMessage` that delivered the same message and the expiry would be discarded as
            // a duplicate — the message would then never leave the screen, which is the bug.
            is WsMessage.MessageExpired -> "${message.messageId}_expired"
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

/**
 * Thrown by [WsClient.send] when the socket was down and the message went onto the offline queue.
 *
 * Distinct from a genuine send failure because the two outcomes are opposite. A queued message
 * *will* go out on the next connect, so a caller that deletes the bubble and reports "could not
 * send" is telling the user something untrue and inviting them to type it again — and then it
 * arrives twice. Callers should leave a queued message on screen as `MessageStatus.SENDING`, which
 * already renders as a pending clock, and let the connection strip explain why it is still waiting.
 *
 * Subclasses [Exception] rather than replacing it so the many `catch (_: Exception)` sites around
 * the send paths keep compiling and keep behaving; only the ones that care need to look.
 */
class MessageQueuedException : Exception("WebSocket not connected")

/**
 * Thrown by [WsClient.send] when a message could not go out **and** could not be queued.
 *
 * The opposite of [MessageQueuedException] in the one way that matters to a caller: nothing was
 * stored and nothing will be sent later, so the optimistic bubble must be removed and the user
 * told.
 *
 * Open, with [ViewOnceNotQueueableException] as its one named subclass, so a caller can either
 * handle the specific reason or catch the whole category. Until now only the view-once case
 * existed, and every *other* way queueing could fail threw [MessageQueuedException] anyway — the
 * message vanished and the user was told it was waiting to be sent.
 */
open class MessageNotQueuedException(message: String) : Exception(message)

/**
 * The queue could not accept the message and there is no more specific reason to give.
 *
 * Reached when there is no local cache at all, when the frame is not a [WsMessage.SendMessage], or
 * when the insert itself fails. Each of those used to end in [MessageQueuedException].
 */
class MessageCouldNotBeQueuedException : MessageNotQueuedException("Message could not be queued")

/**
 * Thrown by [WsClient.send] when a view-once message could not go out and could not be queued.
 *
 * Separate from its parent because the reason is worth saying out loud: the queue cannot carry the
 * view-once flag, so a queued one would arrive unsealed on the next reconnect. See the guard in
 * [WsClient.send].
 */
class ViewOnceNotQueueableException :
    MessageNotQueuedException("View-once message cannot be queued offline")

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    WAITING_FOR_AUTH
}
