package com.muhabbet.app.session

import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.util.Log
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Starts everything a signed-in session runs, and stops it when the session ends (#813).
 *
 * This was `SessionLifecycle` in `App.kt`: six `LaunchedEffect`/`DisposableEffect` blocks keyed on a
 * Boolean read off the navigation stack. Because the key was navigation, an expired session that left
 * the conversation list on screen kept every one of them running — including a socket loop that
 * re-read an empty token store every two seconds until the app was killed. Keyed on
 * [SessionState.activeUserId] instead, an expiry, a logout and an account deletion all stop the same
 * things in the same place, and the whole lifecycle is plain Kotlin that a virtual-time test drives.
 *
 * Two lifetimes, kept apart on purpose:
 * - **The account's session** — the delivery-receipt pump, push-token and E2E key registration, the
 *   background-sync job and the crash identity. These follow [SessionManager.state] alone.
 * - **The socket** additionally needs a UI host ([attachHost]). The connection was always torn down
 *   when the Activity went away, and keeping it up for a process with no screen would show the user
 *   as online after they had closed the app.
 *
 * When the session ends, [WsClient.endSession] also drops what the client remembered *for that
 * account* — the focused conversation and receipts waiting for a reconnect (#829) — so none of it can
 * be replayed under the next account's token.
 */
class SessionCoordinator(
    private val sessionManager: SessionManager,
    private val wsClient: WsClient,
    private val sessionWiring: SessionWiring,
    private val registerPushToken: suspend (languageTag: String) -> Unit,
    private val registerE2EKeys: suspend () -> Unit,
    private val scope: CoroutineScope
) {

    private val hostCount = MutableStateFlow(0)

    /**
     * The language the app is rendering, published by the composition because only a composable can
     * read it out of `strings.xml` (#469, #548). Push registration re-runs whenever it changes, so the
     * server stops notifying in the language the user just moved away from.
     */
    private val renderedLanguage = MutableStateFlow<String?>(null)

    private var started = false

    /** Begins observing the session. Idempotent: the composition root calls it once per host. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            sessionManager.state
                .map { it.activeUserId }
                .distinctUntilChanged()
                .collectLatest { userId ->
                    if (userId == null) {
                        // Also on a signed-out cold start, deliberately: that is what clears the
                        // WorkManager job and crash identity orphaned by builds that never did.
                        sessionWiring.onSessionEnded()
                    } else {
                        runSession(userId)
                    }
                }
        }
    }

    /** A UI host (the Activity's composition) is present. Pair with [detachHost]. */
    fun attachHost() = hostCount.update { it + 1 }

    /** The UI host that called [attachHost] is gone. */
    fun detachHost() = hostCount.update { (it - 1).coerceAtLeast(0) }

    fun onRenderedLanguage(languageTag: String) {
        renderedLanguage.value = languageTag
    }

    /** Runs until the session for [userId] ends, at which point cancellation tears it all down. */
    private suspend fun runSession(userId: String) {
        try {
            coroutineScope {
                launch { holdSocketWhileHosted() }
                launch { pumpDeliveryReceipts(userId) }
                launch {
                    renderedLanguage.filterNotNull().collectLatest { tag ->
                        runGuarded("register the push token") { registerPushToken(tag) }
                    }
                }
                launch { runGuarded("register E2E keys") { registerE2EKeys() } }
                sessionWiring.onSessionActive()
                awaitCancellation()
            }
        } finally {
            withContext(NonCancellable) {
                wsClient.endSession()
                sessionWiring.onSessionEnded()
            }
        }
    }

    private suspend fun holdSocketWhileHosted() {
        hostCount.map { it > 0 }.distinctUntilChanged().collectLatest { hosted ->
            if (!hosted) return@collectLatest
            val generation = wsClient.connect()
            try {
                awaitCancellation()
            } finally {
                wsClient.disconnect(generation)
            }
        }
    }

    /**
     * DELIVERED for every incoming message, whatever screen is open. `sendAck` never throws for a
     * send failure — it queues the receipt and replays it on the next connect — so a dropped socket
     * cannot kill this pump; only the session ending can.
     */
    private suspend fun pumpDeliveryReceipts(userId: String) {
        wsClient.incoming.collect { message ->
            if (message is WsMessage.NewMessage && message.senderId != userId) {
                val sentNow = wsClient.sendAck(
                    WsMessage.AckMessage(
                        messageId = message.messageId,
                        conversationId = message.conversationId,
                        status = MessageStatus.DELIVERED
                    )
                )
                if (!sentNow) {
                    Log.d(TAG, "DELIVERED receipt for ${message.messageId} queued for the next reconnect")
                }
            }
        }
    }

    /**
     * Neither registration can show the user anything, and E2E is flag-OFF in production; a failure
     * must not end the session or go unrecorded (#264).
     */
    private suspend fun runGuarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to $what: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "SessionCoordinator"
    }
}
