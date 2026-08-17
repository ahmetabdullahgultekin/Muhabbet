package com.muhabbet.app.data.local

/**
 * The offline send-queue, as [com.muhabbet.app.data.remote.WsClient] needs it — and only that.
 *
 * The same extraction, and for the same reason, as [ConversationCache] and [MessageCache]:
 * [LocalCache] is backed by SQLDelight and its driver wants an Android `Context`, so anything that
 * names the concrete class can only be exercised on a device, and this host has no emulator and
 * cannot have one.
 *
 * It matters more here than in the other two. `WsClient`'s queue path is where a message either
 * survives being offline or disappears, and because the concrete class could not be supplied in a
 * test, the test that covered it ran with **no cache at all** — while asserting that the message
 * had been queued. It had not been. The assertion passed for three releases on a fixture that
 * could not possibly queue anything, which is how a message being silently dropped went unnoticed.
 *
 * The three methods below are exactly what `WsClient` calls. [LocalCache] implements them
 * unchanged, and every other caller still depends on the concrete class.
 */
interface PendingMessageCache {
    fun getPendingMessages(): List<PendingMessageData>
    fun insertPendingMessage(msg: PendingMessageData)
    fun deletePendingMessage(id: String)

    /** Bumped by the drain loop when a queued message could not be sent, so a poison row is visible. */
    fun incrementRetryCount(id: String)
}
