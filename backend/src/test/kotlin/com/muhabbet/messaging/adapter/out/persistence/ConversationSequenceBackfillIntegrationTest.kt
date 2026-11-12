package com.muhabbet.messaging.adapter.out.persistence

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * V28's backfill (#750): existing history is numbered per conversation in `(server_timestamp, id)`
 * order, so rows that share an instant get a deterministic order rather than whatever the planner
 * returns; unreleased scheduled rows are left unnumbered; `last_seq` ends at the highest number.
 *
 * No Spring context: this builds the schema up to V27 with Flyway, writes history the way the old
 * code left it, then applies the rest — which is exactly what production will do.
 */
@Testcontainers
class ConversationSequenceBackfillIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("muhabbet_backfill")
            withUsername("muhabbet")
            withPassword("muhabbet_test")
        }
    }

    private fun flyway(target: String?): Flyway = Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .locations("classpath:db/migration")
        .apply { if (target != null) target(target) }
        .load()

    private fun <T> connection(block: (Connection) -> T): T =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use(block)

    /** id → seq, asserting on the way that every row's change_seq equals its seq after backfill. */
    private fun readSeqs(): Map<UUID, Long?> = connection { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT id, seq, change_seq FROM messages").use { rs ->
                buildMap {
                    while (rs.next()) {
                        val seq = rs.getLong("seq").takeUnless { rs.wasNull() }
                        assertEquals(seq, rs.getLong("change_seq").takeUnless { rs.wasNull() })
                        put(rs.getObject("id", UUID::class.java), seq)
                    }
                }
            }
        }
    }

    private fun readLastSeqs(): Map<UUID, Long> = connection { c ->
        c.createStatement().use { st ->
            st.executeQuery("SELECT id, last_seq FROM conversations").use { rs ->
                buildMap { while (rs.next()) put(rs.getObject("id", UUID::class.java), rs.getLong("last_seq")) }
            }
        }
    }

    @Test
    fun `should number existing history by timestamp then id when V28 is applied`() {
        flyway("27").migrate()

        val user = UUID.randomUUID()
        val chatA = UUID.randomUUID()
        val chatB = UUID.randomUUID()
        val instant = Instant.parse("2026-11-01T12:00:00Z")
        // Three rows at the same instant, inserted out of id order, plus one earlier and one later.
        val tieIds = listOf(
            UUID.fromString("00000000-0000-7000-8000-000000000003"),
            UUID.fromString("00000000-0000-7000-8000-000000000001"),
            UUID.fromString("00000000-0000-7000-8000-000000000002")
        )
        val earlier = UUID.randomUUID()
        val later = UUID.randomUUID()
        val scheduled = UUID.randomUUID()
        val otherChat = UUID.randomUUID()

        connection { c ->
            c.prepareStatement("INSERT INTO users (id, phone_number) VALUES (?, '+905000000099')").use {
                it.setObject(1, user); it.executeUpdate()
            }
            listOf(chatA, chatB).forEach { id ->
                c.prepareStatement("INSERT INTO conversations (id, type) VALUES (?, 'group')").use {
                    it.setObject(1, id); it.executeUpdate()
                }
            }
            fun insert(id: UUID, conv: UUID, at: Instant, isScheduled: Boolean = false) =
                c.prepareStatement(
                    "INSERT INTO messages (id, conversation_id, sender_id, content, server_timestamp, " +
                        "client_timestamp, is_scheduled) VALUES (?, ?, ?, 'x', ?, ?, ?)"
                ).use {
                    it.setObject(1, id); it.setObject(2, conv); it.setObject(3, user)
                    it.setTimestamp(4, Timestamp.from(at)); it.setTimestamp(5, Timestamp.from(at))
                    it.setBoolean(6, isScheduled)
                    it.executeUpdate()
                }
            insert(later, chatA, instant.plusSeconds(1))
            tieIds.forEach { insert(it, chatA, instant) }
            insert(earlier, chatA, instant.minusSeconds(1))
            insert(scheduled, chatA, instant.minusSeconds(5), isScheduled = true)
            insert(otherChat, chatB, instant)
        }

        flyway(null).migrate()

        val seqs = readSeqs()
        val lastSeq = readLastSeqs()

        val sortedTies = tieIds.sorted()
        assertEquals(1L, seqs[earlier])
        assertEquals(listOf(2L, 3L, 4L), sortedTies.map { seqs[it] }, "equal timestamps are ordered by id")
        assertEquals(5L, seqs[later])
        assertNull(seqs[scheduled], "an unreleased scheduled row is numbered at release")
        assertEquals(1L, seqs[otherChat], "numbering is per conversation")
        assertEquals(5L, lastSeq[chatA])
        assertEquals(1L, lastSeq[chatB])
    }
}
