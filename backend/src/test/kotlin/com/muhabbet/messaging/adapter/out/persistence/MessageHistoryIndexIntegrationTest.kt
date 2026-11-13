package com.muhabbet.messaging.adapter.out.persistence

import com.redis.testcontainers.RedisContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * The index half of #920 and the migration half of #921, against a database Flyway built with the
 * application's own configuration — so this also proves V28's `CREATE INDEX CONCURRENTLY` runs to
 * completion under it, which it does not with Flyway's default transactional lock.
 *
 * The EXPLAIN assertions run over a realistically shaped table (200 conversations, 60,000
 * messages, a handful deleted and scheduled), analyzed, so the planner is choosing between real
 * alternatives rather than defaulting to a sequential scan of a tiny table. The SQL is the
 * statement Hibernate generates for the JPQL in `SpringDataMessageRepository`, with literals for the
 * parameters.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class MessageHistoryIndexIntegrationTest {

    @Autowired private lateinit var jdbc: JdbcTemplate

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("muhabbet_test")
            withUsername("muhabbet")
            withPassword("muhabbet_test")
        }

        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.redisHost }
            registry.add("spring.data.redis.port") { redis.redisPort }
        }

        private const val SENDER = "00000000-0000-0000-0000-00000000a001"
        private const val CONVERSATIONS = 200
        private const val MESSAGES_PER_CONVERSATION = 300
        private const val HISTORY_INDEX = "idx_messages_conv_history"

        /** Seeded once per class: the table is read-only for every test here. */
        private var seeded = false

        private const val CONVERSATION = "00000000-0000-0000-0001-000000000042"

        private val DROPPED = listOf(
            "idx_messages_conversation_time",
            "idx_messages_conversation_latest",
            "idx_messages_conv_latest_covering",
            "idx_bots_api_token",
            "idx_devices_user",
            "idx_poll_votes_message",
            "idx_reactions_message",
            "idx_user_blocks_blocker",
            "idx_channel_subs_channel",
            "idx_community_groups_community",
            "idx_group_call_participants_call",
            "idx_chat_wallpapers_user"
        )
    }

    private val conversation = CONVERSATION

    /**
     * Not `@BeforeAll`: that needs a per-class test instance, which Spring autowires before the
     * Testcontainers extension has started the static containers.
     */
    @BeforeEach
    fun seed() {
        if (seeded) return
        jdbc.update("INSERT INTO users (id, phone_number) VALUES (?::uuid, '+905001112233')", SENDER)
        jdbc.update(
            """
            INSERT INTO conversations (id, type, created_by)
            SELECT ('00000000-0000-0000-0001-' || lpad(g::text, 12, '0'))::uuid, 'GROUP', ?::uuid
            FROM generate_series(1, $CONVERSATIONS) g
            """.trimIndent(),
            SENDER
        )
        jdbc.update(
            """
            INSERT INTO messages (id, conversation_id, sender_id, content, server_timestamp, client_timestamp, is_deleted, is_scheduled)
            SELECT gen_random_uuid(),
                   ('00000000-0000-0000-0001-' || lpad(c::text, 12, '0'))::uuid,
                   ?::uuid,
                   'message ' || m || ' in ' || c,
                   now() - (m || ' minutes')::interval,
                   now() - (m || ' minutes')::interval,
                   m % 97 = 0,
                   m % 113 = 0
            FROM generate_series(1, $CONVERSATIONS) c, generate_series(1, $MESSAGES_PER_CONVERSATION) m
            """.trimIndent(),
            SENDER
        )
        jdbc.execute("ANALYZE messages")
        seeded = true
    }

    private fun plan(sql: String): String =
        jdbc.queryForList("EXPLAIN $sql", String::class.java).joinToString("\n")

    @Test
    fun `should replace the three overlapping history indexes with one valid keyset index`() {
        val indexes = jdbc.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
            String::class.java
        )
        assertThat(indexes).contains(HISTORY_INDEX).doesNotContainAnyElementsOf(DROPPED)
        // A CONCURRENTLY build that was interrupted leaves an INVALID index the planner ignores.
        val valid = jdbc.queryForObject(
            "SELECT i.indisvalid FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid WHERE c.relname = ?",
            Boolean::class.java,
            HISTORY_INDEX
        )
        assertThat(valid).isTrue()
        // The bots token is still unique-indexed — by its constraint, which is the one that stays.
        assertThat(indexes).contains("bots_api_token_key")
    }

    @Test
    fun `should serve the newest history page from the index without a sort`() {
        val plan = plan(
            """
            SELECT * FROM messages m
            WHERE m.conversation_id = '$conversation' AND m.is_deleted = false AND m.is_scheduled = false
              AND m.server_timestamp >= 'epoch'
            ORDER BY m.server_timestamp DESC, m.id DESC LIMIT 51
            """.trimIndent()
        )
        assertThat(plan).contains(HISTORY_INDEX).doesNotContain("Sort").doesNotContain("Seq Scan")
    }

    @Test
    fun `should turn the keyset row comparison into an index condition`() {
        val plan = plan(
            """
            SELECT * FROM messages m
            WHERE m.conversation_id = '$conversation' AND m.is_deleted = false AND m.is_scheduled = false
              AND m.server_timestamp >= 'epoch'
              AND (m.server_timestamp, m.id) < (now() - interval '150 minutes', '00000000-0000-0000-0000-000000000000')
            ORDER BY m.server_timestamp DESC, m.id DESC LIMIT 51
            """.trimIndent()
        )
        assertThat(plan).contains(HISTORY_INDEX).doesNotContain("Sort")
        // In the Index Cond, not a Filter applied to every row the scan walks past.
        val indexCond = plan.lines().first { it.contains("Index Cond") }
        assertThat(indexCond).contains("ROW(server_timestamp, id) <")
    }

    @Test
    fun `should serve the forward keyset from the same index scanned backwards`() {
        val plan = plan(
            """
            SELECT * FROM messages m
            WHERE m.conversation_id = '$conversation' AND m.is_deleted = false AND m.is_scheduled = false
              AND m.server_timestamp >= 'epoch'
              AND (m.server_timestamp, m.id) > (now() - interval '150 minutes', '00000000-0000-0000-0000-000000000000')
            ORDER BY m.server_timestamp ASC, m.id ASC LIMIT 51
            """.trimIndent()
        )
        assertThat(plan).contains("Index Scan Backward using $HISTORY_INDEX").doesNotContain("Sort")
    }

    @Test
    fun `should find each conversation's newest message through the index`() {
        val plan = plan(
            """
            SELECT m.* FROM messages m
            WHERE m.is_deleted = false AND m.is_scheduled = false
              AND m.conversation_id IN ('$conversation', '00000000-0000-0000-0001-000000000043')
              AND m.server_timestamp = (
                SELECT max(m2.server_timestamp) FROM messages m2
                WHERE m2.conversation_id = m.conversation_id AND m2.is_deleted = false AND m2.is_scheduled = false
              )
            """.trimIndent()
        )
        assertThat(plan).contains(HISTORY_INDEX).doesNotContain("Seq Scan on messages")
    }

    /**
     * The recurrence guard #920 asks for: no index whose key is a prefix of another index on the same
     * table, with the same predicate and opclasses, unless it is the unique one. That is exactly the
     * shape of the three history indexes and of `idx_bots_api_token`, and it is how the next one would
     * look — an index added for one query without looking at what was already there.
     */
    @Test
    fun `should have no index made redundant by another`() {
        data class Idx(
            val name: String,
            val table: String,
            val unique: Boolean,
            val keys: List<String>,
            val predicate: String?,
            val method: String
        )
        val rows = jdbc.query(
            """
            SELECT c.relname AS name, t.relname AS tbl, i.indisunique AS uniq, am.amname AS method,
                   i.indnkeyatts AS nkeys, i.indkey::text AS indkey, i.indoption::text AS indoption,
                   i.indclass::text AS indclass, pg_get_expr(i.indpred, i.indrelid) AS pred,
                   i.indexprs IS NOT NULL AS has_exprs
            FROM pg_index i
            JOIN pg_class c ON c.oid = i.indexrelid
            JOIN pg_class t ON t.oid = i.indrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            JOIN pg_am am ON am.oid = c.relam
            WHERE n.nspname = 'public'
            """.trimIndent()
        ) { rs, _ ->
            val n = rs.getInt("nkeys")
            val cols = rs.getString("indkey").split(" ").take(n)
            val opts = rs.getString("indoption").split(" ").take(n)
            val classes = rs.getString("indclass").split(" ").take(n)
            // An expression index is identified by its expressions, not its column numbers (0).
            if (rs.getBoolean("has_exprs")) {
                null
            } else {
                Idx(
                    name = rs.getString("name"),
                    table = rs.getString("tbl"),
                    unique = rs.getBoolean("uniq"),
                    keys = cols.indices.map { "${cols[it]}/${opts[it]}/${classes[it]}" },
                    predicate = rs.getString("pred"),
                    method = rs.getString("method")
                )
            }
        }.filterNotNull()

        val redundant = rows.flatMap { a ->
            rows.filter { b ->
                a.name != b.name && a.table == b.table && a.method == b.method && a.predicate == b.predicate &&
                    a.keys.size <= b.keys.size && b.keys.subList(0, a.keys.size) == a.keys &&
                    // A unique index enforces something; it is only redundant beside an identical unique one.
                    (!a.unique || (b.unique && a.keys == b.keys && a.name > b.name))
            }.map { b -> "${a.table}: ${a.name} is covered by ${b.name}" }
        }
        assertThat(redundant).isEmpty()
    }
}
