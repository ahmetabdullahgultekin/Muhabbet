package com.muhabbet.app.data.local

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.muhabbet.app.data.outbox.OutboxState
import com.muhabbet.app.data.outbox.OutgoingMessage
import com.muhabbet.app.data.outbox.SendFailure
import com.muhabbet.app.db.MuhabbetDatabase
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.protocol.WsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The on-device upgrade (#795): schema v1, as every installed build has it, migrated by `1.sqm`.
 *
 * Run against real SQLite on the JVM rather than a fake, because the failure this guards against is
 * a schema change that compiles and then meets an existing database: SQLDelight only runs a
 * migration if one exists, and a `.sq` edit without one leaves every upgraded phone on the old
 * tables while fresh installs get the new ones.
 */
class OutboxSchemaMigrationTest {

    @Test
    fun should_migrate_a_v1_database_to_exactly_the_schema_a_fresh_install_gets() {
        val migrated = v1Database()
        MuhabbetDatabase.Schema.migrate(migrated, 1, MuhabbetDatabase.Schema.version)

        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MuhabbetDatabase.Schema.create(fresh)

        assertEquals(2L, MuhabbetDatabase.Schema.version)
        assertEquals(schemaOf(fresh), schemaOf(migrated))
    }

    @Test
    fun should_carry_messages_an_older_build_queued_into_the_outbox() {
        val driver = v1Database()
        driver.execute(
            null,
            """INSERT INTO pendingMessage(id, messageId, conversationId, contentType, content, replyToId, mediaUrl, clientTimestamp, retryCount, createdAt)
               VALUES ('req-1', 'msg-1', 'conv-1', 'IMAGE', 'Photo', 'reply-1', 'https://cdn/x.jpg', '2026-11-01T10:00:00Z', 4, '2026-11-01T10:00:00Z')""",
            0
        )
        MuhabbetDatabase.Schema.migrate(driver, 1, MuhabbetDatabase.Schema.version)
        val database = MuhabbetDatabase(driver)

        val loaded = SqlOutboxStore(database).loadAll()

        val only = loaded.single()
        assertEquals(OutboxState.Queued, only.state)
        assertEquals(0, only.attempts, "the old per-reconnect counter is not carried into the new budget")
        assertEquals(
            WsMessage.SendMessage(
                requestId = "req-1", messageId = "msg-1", conversationId = "conv-1", content = "Photo",
                contentType = ContentType.IMAGE, replyToId = "reply-1", mediaUrl = "https://cdn/x.jpg"
            ),
            only.frame
        )
        assertTrue(database.muhabbetDatabaseQueries.getPendingMessages().executeAsList().isEmpty(), "imported once, not twice")
        assertEquals(1, SqlOutboxStore(database).loadAll().size)
    }

    @Test
    fun should_round_trip_every_frame_field_and_every_stored_state() {
        val database = freshDatabase()
        val store = SqlOutboxStore(database)
        val frame = WsMessage.SendMessage(
            requestId = "r", messageId = "m", conversationId = "c", content = "Photo",
            contentType = ContentType.IMAGE, replyToId = "q", mediaUrl = "https://cdn/x.jpg",
            thumbnailUrl = "https://cdn/x_t.jpg", mediaId = "media-1", forwardedFrom = "orig",
            viewOnce = true, scheduledAt = 1_800_000_000_000
        )
        val states = listOf(
            OutboxState.Queued,
            OutboxState.Failed(SendFailure.Exhausted),
            OutboxState.Failed(SendFailure.Rejected("MSG_NOT_MEMBER")),
            OutboxState.Failed(SendFailure.Rejected(null))
        )

        for (state in states) {
            store.save(OutgoingMessage(frame, state, attempts = 3, createdAtMs = 42))
            val loaded = store.loadAll().single()
            assertEquals(frame, loaded.frame)
            assertEquals(state, loaded.state)
            assertEquals(3, loaded.attempts)
            assertEquals(42, loaded.createdAtMs)
        }

        store.save(OutgoingMessage(frame, OutboxState.Sending(attempt = 2, deadlineMs = 9), attempts = 1, createdAtMs = 42))
        assertTrue(store.loadAll().single().state is OutboxState.Sending, "the Outbox re-queues it, the store reports it")

        store.remove("m")
        assertTrue(store.loadAll().isEmpty())
    }

    @Test
    fun should_load_the_outbox_oldest_first() {
        val store = SqlOutboxStore(freshDatabase())
        listOf(30L to "c", 10L to "a", 20L to "b").forEach { (at, id) ->
            store.save(
                OutgoingMessage(
                    WsMessage.SendMessage(requestId = "r$id", messageId = id, conversationId = "c", content = id),
                    OutboxState.Queued,
                    createdAtMs = at
                )
            )
        }
        assertEquals(listOf("a", "b", "c"), store.loadAll().map { it.messageId })
    }

    @Test
    fun should_only_ever_move_the_watermark_forward() {
        val watermarks = SqlSyncWatermarkStore(freshDatabase())
        assertEquals(null, watermarks.read())

        watermarks.advanceTo(2_000)
        watermarks.advanceTo(1_000)
        assertEquals(2_000L, watermarks.read())

        watermarks.advanceTo(3_000)
        assertEquals(3_000L, watermarks.read())
    }

    private fun freshDatabase(): MuhabbetDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MuhabbetDatabase.Schema.create(driver)
        return MuhabbetDatabase(driver)
    }

    /** Schema version 1, verbatim from the `.sq` every installed build was generated from. */
    private fun v1Database(): SqlDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        V1_SCHEMA.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { driver.execute(null, it, 0) }
        return driver
    }

    private fun schemaOf(driver: SqlDriver): List<String> =
        driver.executeQuery(
            null,
            "SELECT type || ' ' || name || ' ' || sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' ORDER BY type, name",
            { cursor ->
                val rows = mutableListOf<String>()
                while (cursor.next().value) rows += (cursor.getString(0) ?: "").replace(Regex("\\s+"), " ")
                app.cash.sqldelight.db.QueryResult.Value(rows)
            },
            0
        ).value

    private companion object {
        val V1_SCHEMA = """
            CREATE TABLE cachedConversation (
                id TEXT NOT NULL PRIMARY KEY,
                type TEXT NOT NULL,
                name TEXT,
                avatarUrl TEXT,
                lastMessagePreview TEXT,
                lastMessageAt TEXT,
                unreadCount INTEGER NOT NULL DEFAULT 0,
                createdAt TEXT NOT NULL,
                disappearAfterSeconds INTEGER,
                isPinned INTEGER NOT NULL DEFAULT 0,
                participantsJson TEXT NOT NULL DEFAULT '[]',
                updatedAt TEXT NOT NULL
            );
            CREATE INDEX idx_conv_updated ON cachedConversation(updatedAt);
            CREATE INDEX idx_conv_pinned ON cachedConversation(isPinned);
            CREATE TABLE cachedMessage (
                id TEXT NOT NULL PRIMARY KEY,
                conversationId TEXT NOT NULL,
                senderId TEXT NOT NULL,
                contentType TEXT NOT NULL,
                content TEXT NOT NULL,
                replyToId TEXT,
                mediaUrl TEXT,
                thumbnailUrl TEXT,
                status TEXT NOT NULL DEFAULT 'SENDING',
                serverTimestamp TEXT,
                clientTimestamp TEXT NOT NULL,
                editedAt TEXT,
                isDeleted INTEGER NOT NULL DEFAULT 0,
                forwardedFrom TEXT,
                reactionsJson TEXT NOT NULL DEFAULT '{}',
                myReactionsJson TEXT NOT NULL DEFAULT '[]',
                senderName TEXT,
                senderAvatarUrl TEXT
            );
            CREATE INDEX idx_msg_conv ON cachedMessage(conversationId, clientTimestamp);
            CREATE INDEX idx_msg_status ON cachedMessage(status);
            CREATE TABLE pendingMessage (
                id TEXT NOT NULL PRIMARY KEY,
                messageId TEXT NOT NULL,
                conversationId TEXT NOT NULL,
                contentType TEXT NOT NULL,
                content TEXT NOT NULL,
                replyToId TEXT,
                mediaUrl TEXT,
                clientTimestamp TEXT NOT NULL,
                retryCount INTEGER NOT NULL DEFAULT 0,
                createdAt TEXT NOT NULL
            );
            CREATE INDEX idx_pending_created ON pendingMessage(createdAt)
        """.trimIndent()
    }
}
