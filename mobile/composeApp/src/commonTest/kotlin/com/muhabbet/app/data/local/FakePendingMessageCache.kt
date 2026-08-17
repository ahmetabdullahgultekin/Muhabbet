package com.muhabbet.app.data.local

/**
 * An in-memory offline send-queue, so the queue path can be exercised without SQLDelight.
 *
 * [failOnInsert] stages the third way queueing can fail. The other two — no cache at all, and a
 * frame that is not a `SendMessage` — need no fake; they are reached by passing `null` and by
 * sending something else.
 */
class FakePendingMessageCache(
    private val failOnInsert: Boolean = false
) : PendingMessageCache {

    val queued = mutableListOf<PendingMessageData>()

    override fun getPendingMessages(): List<PendingMessageData> = queued.toList()

    override fun insertPendingMessage(msg: PendingMessageData) {
        if (failOnInsert) throw IllegalStateException("disk full")
        queued += msg
    }

    override fun deletePendingMessage(id: String) {
        queued.removeAll { it.id == id }
    }

    val retries = mutableMapOf<String, Int>()

    override fun incrementRetryCount(id: String) {
        retries[id] = (retries[id] ?: 0) + 1
    }
}
