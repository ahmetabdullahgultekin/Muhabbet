package com.muhabbet.app.data.remote

import com.muhabbet.app.data.local.MessageCache
import com.muhabbet.app.data.local.SessionDataGate
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The app-wide DELIVERED receipt, sent only once the message is on the device (#753, ADR 0013 P4).
 *
 * A DELIVERED receipt is what gives the sender two ticks, so it is a claim that this phone *has*
 * the message. It used to be sent the moment a `message.new` frame arrived, while nothing wrote the
 * frame anywhere: [WsClient.incoming] does not replay, so a message that arrived with no chat open
 * existed only in memory, and a process death a second later lost it while the sender saw it as
 * delivered. Writing it to the local cache first is what makes the claim true; a write that fails
 * sends no receipt. The server does not push the message again over the socket — it stays at one
 * tick for the sender, and this device picks it up on its next history fetch or background sync.
 *
 * Writes go through [gate], so one still in flight when the session ends cannot land after the
 * logout wipe and leave the departed account's message on disk.
 *
 * The **only** sender of DELIVERED over the socket. The conversation list used to send a second one
 * for every message from its own collector (#346); that one is gone, and a screen must not add it
 * back — this runs for every screen.
 *
 * A plain class rather than the inline `LaunchedEffect` it replaces, so the order can be tested
 * without an emulator. `App.kt` runs it in an effect keyed on the session.
 *
 * @param storageContext where the cache write runs. SQLite on the main thread is a jank source on a
 *   busy group; tests pass `EmptyCoroutineContext` to stay on the test scheduler.
 */
class DeliveryReceiptPump(
    private val incoming: Flow<WsMessage>,
    private val messageCache: MessageCache,
    private val sendAck: suspend (WsMessage.AckMessage) -> Boolean,
    private val gate: SessionDataGate,
    private val storageContext: CoroutineContext = Dispatchers.Default,
    private val clock: () -> Instant = { Clock.System.now() }
) {

    /** Collects until cancelled — which is the session ending. */
    suspend fun run(currentUserId: String) {
        val session = gate.currentSession()
        incoming.collect { message ->
            if (message is WsMessage.NewMessage && message.senderId != currentUserId) {
                persistThenAcknowledge(message, session)
            }
        }
    }

    private suspend fun persistThenAcknowledge(message: WsMessage.NewMessage, session: Long) {
        val stored = runCatchingCancellable {
            gate.writeIfCurrent(session) {
                withContext(storageContext) {
                    messageCache.upsertMessages(
                        listOf(message.toMessage(receivedAt = clock()).copy(status = MessageStatus.DELIVERED))
                    )
                }
            }
        }
        if (stored.getOrNull() == null) {
            // No receipt — the write failed, or the session ended under it. The sender keeps one
            // tick, which is the truth: this device does not hold the message, and picks it up on
            // its next history fetch or sync.
            Log.e(TAG, "Could not store ${message.messageId}; DELIVERED withheld", stored.exceptionOrNull())
            return
        }
        // sendAck() never throws for a send failure — it queues the receipt and replays it on the
        // next connect — so a dropped socket cannot end this pump. Cancellation still propagates.
        val sentNow = sendAck(
            WsMessage.AckMessage(
                messageId = message.messageId,
                conversationId = message.conversationId,
                status = MessageStatus.DELIVERED
            )
        )
        if (!sentNow) Log.d(TAG, "DELIVERED receipt for ${message.messageId} queued for the next reconnect")
    }

    private companion object {
        const val TAG = "DeliveryReceiptPump"
    }
}
