package com.muhabbet.app.data.outbox

/**
 * Where the outbox keeps the messages that must survive a process death (out-port).
 *
 * Only unconfirmed messages are stored ([OutboxState.isPersistent]); the server owns everything
 * after its ack. An interface so [Outbox] can be driven in `commonTest` without a SQLDelight driver;
 * production uses `SqlOutboxStore`.
 *
 * Every method may throw — a full disk is real. [Outbox] decides what that means for the user.
 */
interface OutboxStore {
    fun loadAll(): List<OutgoingMessage>
    fun save(message: OutgoingMessage)
    fun remove(messageId: String)
}
