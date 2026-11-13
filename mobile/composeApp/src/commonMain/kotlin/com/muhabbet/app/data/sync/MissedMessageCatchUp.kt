package com.muhabbet.app.data.sync

import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.protocol.WsMessage
import kotlin.time.Instant

/**
 * The newest server timestamp this device has seen (out-port). Persisted, so a gap that spans a
 * process death is still filled. Implementations keep the maximum: [advanceTo] never moves it back.
 */
interface SyncWatermarkStore {
    fun read(): Long?
    fun advanceTo(epochMillis: Long)
}

/**
 * What a socket that was down missed, fetched once the replacement is live (#797).
 *
 * The real-time stream has no sequence and no replay: a frame emitted while the socket was down is
 * simply gone, so after any network blip an open chat silently lacked whatever arrived in the gap
 * until the user left and came back. This records the newest server timestamp [observe]d on the
 * live stream and, on every transition into Live, asks `GET /messages/since` for everything after
 * it. The result goes through the very same path as live frames — [WsClient] replays it as
 * `message.new` through its de-duplication — so receipts, the conversation list and an open chat all
 * learn about it exactly as if it had arrived live.
 *
 * Server time only. The device clock never sets the watermark, so a phone with a wrong clock can
 * neither skip messages nor re-download history.
 *
 * Not covered, because the endpoint does not return them: edits, deletions and receipts made during
 * the gap. That needs a server-side change feed (#750).
 */
class MissedMessageCatchUp(
    private val store: SyncWatermarkStore,
    private val fetchSince: suspend (isoTimestamp: String) -> List<Message>
) {

    /** Records the server time carried by a live frame. Cheap; called for every inbound frame. */
    fun observe(frame: WsMessage) {
        val serverTime = when (frame) {
            is WsMessage.NewMessage -> frame.serverTimestamp
            is WsMessage.ServerAck -> frame.serverTimestamp
            else -> null
        } ?: return
        advance(serverTime)
    }

    /**
     * Everything after the watermark, oldest first, as the frames the live stream would have carried.
     * Empty on a first session (nothing to measure a gap from — the chat loads history anyway) and on
     * any failure, which is logged and retried on the next connect.
     */
    suspend fun missed(): List<WsMessage.NewMessage> {
        val since = runCatchingCancellable { store.read() }.getOrNull() ?: return emptyList()
        val fetched = runCatchingCancellable { fetchSince(Instant.fromEpochMilliseconds(since).toString()) }
            .onFailure { e -> Log.w(TAG, "Gap-fill fetch failed: ${e.message}") }
            .getOrNull()
            ?: return emptyList()
        val frames = fetched
            .filter { (it.serverTimestamp?.toEpochMilliseconds() ?: 0) > since }
            .sortedBy { it.serverTimestamp }
            .map { it.toNewMessage() }
        frames.lastOrNull()?.let { advance(it.serverTimestamp) }
        if (frames.isNotEmpty()) Log.d(TAG, "Gap-fill: ${frames.size} message(s) missed while disconnected")
        return frames
    }

    private fun advance(epochMillis: Long) {
        runCatchingCancellable { store.advanceTo(epochMillis) }
            .onFailure { e -> Log.w(TAG, "Could not record the sync watermark: ${e.message}") }
    }

    private companion object {
        const val TAG = "CatchUp"
    }
}

/** The frame the live stream would have delivered for [this]. */
internal fun Message.toNewMessage(): WsMessage.NewMessage = WsMessage.NewMessage(
    messageId = id,
    conversationId = conversationId,
    senderId = senderId,
    senderName = null,
    content = content,
    contentType = contentType,
    replyToId = replyToId,
    mediaUrl = mediaUrl,
    thumbnailUrl = thumbnailUrl,
    serverTimestamp = serverTimestamp?.toEpochMilliseconds() ?: 0L,
    forwardedFrom = forwardedFrom,
    viewOnce = viewOnce,
    expiresAt = expiresAt?.toEpochMilliseconds()
)
