package com.muhabbet.app.data.local

import com.muhabbet.app.data.outbox.OutboxState
import com.muhabbet.app.data.outbox.OutboxStore
import com.muhabbet.app.data.outbox.OutgoingMessage
import com.muhabbet.app.data.outbox.SendFailure
import com.muhabbet.app.util.Log
import com.muhabbet.app.db.MuhabbetDatabase
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.shared.protocol.wsJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.time.Instant

/**
 * [OutboxStore] on SQLDelight: the `outboxMessage` table added by migration `1.sqm`.
 *
 * The frame is stored whole, as the same JSON the socket carries (encoded through the sealed base so
 * the `type` discriminator is present), so whatever the protocol adds to `message.send` survives
 * the round trip without a schema change (#795).
 *
 * Also the importer for the v1 `pendingMessage` table: rows an older build queued are turned into
 * outbox rows on first load and deleted, in one transaction. That is the "expand" half of the
 * migration; the legacy table is dropped in a later one.
 */
class SqlOutboxStore(private val database: MuhabbetDatabase) : OutboxStore {

    private val queries = database.muhabbetDatabaseQueries

    override fun loadAll(): List<OutgoingMessage> {
        importLegacyRows()
        return queries.getOutbox().executeAsList().mapNotNull { row ->
            val frame = runCatching { wsJson.decodeFromString<WsMessage>(row.frameJson) as WsMessage.SendMessage }
                .onFailure { e -> Log.e(TAG, "Unreadable outbox row ${row.messageId}: ${e.message}") }
                .getOrNull() ?: return@mapNotNull null
            OutgoingMessage(
                frame = frame,
                state = decodeState(row.state, row.failureCode),
                attempts = row.attempts.toInt(),
                createdAtMs = row.createdAt
            )
        }
    }

    override fun save(message: OutgoingMessage) {
        val (state, failureCode) = encodeState(message.state)
        queries.upsertOutbox(
            messageId = message.messageId,
            conversationId = message.conversationId,
            frameJson = wsJson.encodeToString<WsMessage>(message.frame),
            state = state,
            failureCode = failureCode,
            attempts = message.attempts.toLong(),
            createdAt = message.createdAtMs
        )
    }

    override fun remove(messageId: String) {
        queries.deleteOutbox(messageId)
    }

    private fun importLegacyRows() {
        database.transaction {
            queries.getPendingMessages().executeAsList().forEach { row ->
                val frame = WsMessage.SendMessage(
                    requestId = row.id,
                    messageId = row.messageId,
                    conversationId = row.conversationId,
                    content = row.content,
                    contentType = ContentType.entries.firstOrNull { it.name == row.contentType } ?: ContentType.TEXT,
                    replyToId = row.replyToId,
                    mediaUrl = row.mediaUrl
                )
                val createdAt = runCatching { Instant.parse(row.createdAt).toEpochMilliseconds() }.getOrDefault(0L)
                save(OutgoingMessage(frame = frame, state = OutboxState.Queued, createdAtMs = createdAt))
                queries.deletePendingMessage(row.id)
            }
        }
    }

    private fun encodeState(state: OutboxState): Pair<String, String?> = when (state) {
        OutboxState.Queued -> STATE_QUEUED to null
        is OutboxState.Sending -> STATE_SENDING to null
        is OutboxState.Failed -> when (val failure = state.failure) {
            is SendFailure.Rejected -> STATE_FAILED to (failure.code ?: CODE_UNKNOWN)
            SendFailure.Exhausted -> STATE_FAILED to CODE_EXHAUSTED
        }
        // Confirmed states are never stored; the Outbox removes the row instead.
        else -> error("Confirmed outbox state $state is not persistable")
    }

    private fun decodeState(state: String, failureCode: String?): OutboxState = when (state) {
        STATE_FAILED -> OutboxState.Failed(
            when (failureCode) {
                CODE_EXHAUSTED -> SendFailure.Exhausted
                CODE_UNKNOWN, null -> SendFailure.Rejected(null)
                else -> SendFailure.Rejected(failureCode)
            }
        )
        // Sending is restored as such; the Outbox decides that an interrupted dispatch re-queues.
        STATE_SENDING -> OutboxState.Sending(attempt = 0, deadlineMs = 0)
        else -> OutboxState.Queued
    }

    private companion object {
        const val TAG = "SqlOutboxStore"
        const val STATE_QUEUED = "QUEUED"
        const val STATE_SENDING = "SENDING"
        const val STATE_FAILED = "FAILED"
        const val CODE_EXHAUSTED = "OUTBOX_EXHAUSTED"
        const val CODE_UNKNOWN = "OUTBOX_UNKNOWN"
    }
}
