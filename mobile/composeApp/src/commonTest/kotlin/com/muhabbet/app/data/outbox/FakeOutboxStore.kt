package com.muhabbet.app.data.outbox

/**
 * An in-memory [OutboxStore] that behaves like the table: what is saved is what a fresh process
 * loads. [failWrites] stages a full disk.
 */
class FakeOutboxStore(var failWrites: Boolean = false) : OutboxStore {

    val rows = LinkedHashMap<String, OutgoingMessage>()

    override fun loadAll(): List<OutgoingMessage> = rows.values.sortedBy { it.createdAtMs }

    override fun save(message: OutgoingMessage) {
        if (failWrites) throw IllegalStateException("disk full")
        rows[message.messageId] = message
    }

    override fun remove(messageId: String) {
        rows.remove(messageId)
    }
}
