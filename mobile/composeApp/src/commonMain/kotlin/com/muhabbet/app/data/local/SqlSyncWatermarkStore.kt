package com.muhabbet.app.data.local

import com.muhabbet.app.data.sync.SyncWatermarkStore
import com.muhabbet.app.db.MuhabbetDatabase

/**
 * [SyncWatermarkStore] on the `syncWatermark` table (migration `1.sqm`).
 *
 * The maximum is taken here, in a transaction, rather than with an UPSERT: `ON CONFLICT DO UPDATE`
 * needs SQLite 3.24, and Android 8 (minSdk 26) ships 3.18.
 */
class SqlSyncWatermarkStore(private val database: MuhabbetDatabase) : SyncWatermarkStore {

    private val queries = database.muhabbetDatabaseQueries

    override fun read(): Long? = queries.getWatermark(STREAM_MESSAGES).executeAsOneOrNull()

    override fun advanceTo(epochMillis: Long) {
        database.transaction {
            val current = queries.getWatermark(STREAM_MESSAGES).executeAsOneOrNull()
            if (current == null || epochMillis > current) queries.putWatermark(STREAM_MESSAGES, epochMillis)
        }
    }

    private companion object {
        const val STREAM_MESSAGES = "messages"
    }
}
