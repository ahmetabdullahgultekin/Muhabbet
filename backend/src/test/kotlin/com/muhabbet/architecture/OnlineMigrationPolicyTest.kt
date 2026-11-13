package com.muhabbet.architecture

import com.muhabbet.architecture.OnlineMigrationLint.Rule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Enforces docs/adr/0015-online-safe-migrations.md over every Flyway migration written after the
 * policy (#921). V1–V27 predate it and are grandfathered: they have already run in production, and
 * Flyway's checksum validation forbids editing them anyway.
 */
class OnlineMigrationPolicyTest {

    companion object {
        /** The last migration written before the policy existed. Never raise this. */
        private const val GRANDFATHERED_THROUGH = 27

        private val MIGRATION = Regex("""^V(\d+)__[a-z0-9_]+\.sql$""")
    }

    private val migrationDir: File by lazy {
        val url = requireNotNull(javaClass.classLoader.getResource("db/migration")) { "db/migration not on the test classpath" }
        File(url.toURI())
    }

    private fun migrations(): List<Pair<Int, File>> =
        migrationDir.listFiles { f -> f.name.endsWith(".sql") }.orEmpty()
            .map { f -> (MIGRATION.matchEntire(f.name)?.groupValues?.get(1)?.toInt() ?: -1) to f }

    private fun runsOutsideTransaction(sql: File): Boolean {
        val conf = File(sql.parentFile, sql.name + ".conf")
        return conf.exists() && conf.readLines().any { it.replace(" ", "").equals("executeInTransaction=false", ignoreCase = true) }
    }

    @Test
    fun `should find every migration named V{n}__{snake_case}`() {
        val misnamed = migrations().filter { it.first < 0 }.map { it.second.name }
        assertThat(misnamed).isEmpty()
        assertThat(migrations()).hasSizeGreaterThan(GRANDFATHERED_THROUGH)
    }

    @Test
    fun `should give every migration a distinct version`() {
        // Two parallel branches each wrote a V24 once; Flyway then refuses to start, which is a
        // backend that will not boot rather than a failing test. This fails first, without Docker.
        val duplicates = migrations().groupBy { it.first }.filterValues { it.size > 1 }.keys
        assertThat(duplicates).isEmpty()
    }

    @Test
    fun `should keep every migration after the policy online-safe`() {
        val violations = migrations()
            .filter { (version, _) -> version > GRANDFATHERED_THROUGH }
            .flatMap { (_, file) -> OnlineMigrationLint.lint(file.name, file.readText(), runsOutsideTransaction(file)) }
        assertThat(violations).withFailMessage { violations.joinToString("\n") }.isEmpty()
    }

    @Test
    fun `should not orphan a script config file`() {
        val sqlNames = migrations().map { it.second.name }.toSet()
        val orphans = migrationDir.listFiles { f -> f.name.endsWith(".sql.conf") }.orEmpty()
            .filter { it.name.removeSuffix(".conf") !in sqlNames }
            .map { it.name }
        assertThat(orphans).isEmpty()
    }

    /** The rules themselves: each one must fire on the shape it names and stay quiet on the fix. */
    @Nested
    inner class Rules {

        private fun flagged(sql: String, outsideTransaction: Boolean = true): Set<Rule> =
            OnlineMigrationLint.lint("V99__t.sql", sql, outsideTransaction).map { it.rule }.toSet()

        private val timeout = "SET lock_timeout = '5s';\n"

        @Test
        fun `should flag a plain CREATE INDEX and accept CONCURRENTLY`() {
            assertThat(flagged(timeout + "CREATE INDEX idx_a ON messages (media_id);")).contains(Rule.INDEX_NOT_CONCURRENT)
            assertThat(flagged(timeout + "CREATE UNIQUE INDEX IF NOT EXISTS idx_a ON t (a);")).contains(Rule.INDEX_NOT_CONCURRENT)
            assertThat(flagged(timeout + "CREATE INDEX CONCURRENTLY idx_a ON messages (media_id);")).isEmpty()
        }

        @Test
        fun `should flag a plain DROP INDEX and accept CONCURRENTLY`() {
            assertThat(flagged(timeout + "DROP INDEX IF EXISTS idx_a;")).contains(Rule.DROP_INDEX_NOT_CONCURRENT)
            assertThat(flagged(timeout + "DROP INDEX CONCURRENTLY IF EXISTS idx_a;")).isEmpty()
        }

        @Test
        fun `should require executeInTransaction false for CONCURRENTLY`() {
            val sql = timeout + "CREATE INDEX CONCURRENTLY idx_a ON t (a);"
            assertThat(flagged(sql, outsideTransaction = false)).contains(Rule.CONCURRENTLY_IN_TRANSACTION)
            assertThat(flagged(sql, outsideTransaction = true)).isEmpty()
        }

        @Test
        fun `should require lock_timeout before the first locking statement`() {
            assertThat(flagged("ALTER TABLE t ADD COLUMN a TEXT;")).contains(Rule.MISSING_LOCK_TIMEOUT)
            assertThat(flagged("ALTER TABLE t ADD COLUMN a TEXT;\nSET lock_timeout = '5s';")).contains(Rule.MISSING_LOCK_TIMEOUT)
            assertThat(flagged(timeout + "ALTER TABLE t ADD COLUMN a TEXT;")).isEmpty()
            // A new table with no foreign key takes no lock anyone else is waiting for.
            assertThat(flagged("CREATE TABLE t (id UUID PRIMARY KEY);")).isEmpty()
            assertThat(flagged("CREATE TABLE t (id UUID PRIMARY KEY, u UUID REFERENCES users(id));"))
                .contains(Rule.MISSING_LOCK_TIMEOUT)
        }

        @Test
        fun `should flag a validated foreign key or check and accept NOT VALID`() {
            assertThat(flagged(timeout + "ALTER TABLE t ADD CONSTRAINT fk FOREIGN KEY (a) REFERENCES u(id);"))
                .contains(Rule.CONSTRAINT_NOT_VALID)
            assertThat(flagged(timeout + "ALTER TABLE t ADD CHECK (a > 0);")).contains(Rule.CONSTRAINT_NOT_VALID)
            assertThat(flagged(timeout + "ALTER TABLE t ADD CONSTRAINT fk FOREIGN KEY (a) REFERENCES u(id) NOT VALID;")).isEmpty()
        }

        @Test
        fun `should flag a unique constraint that builds its own index`() {
            assertThat(flagged(timeout + "ALTER TABLE t ADD CONSTRAINT uq UNIQUE (a, b);")).contains(Rule.UNIQUE_CONSTRAINT_BUILDS_INDEX)
            assertThat(flagged(timeout + "ALTER TABLE t ADD CONSTRAINT uq UNIQUE USING INDEX idx_uq;")).isEmpty()
        }

        @Test
        fun `should flag rewrites - type change, SET NOT NULL, volatile default`() {
            assertThat(flagged(timeout + "ALTER TABLE t ALTER COLUMN a TYPE BIGINT;")).contains(Rule.COLUMN_TYPE_CHANGE)
            assertThat(flagged(timeout + "ALTER TABLE t ALTER COLUMN a SET NOT NULL;")).contains(Rule.SET_NOT_NULL)
            assertThat(flagged(timeout + "ALTER TABLE t ADD COLUMN a UUID NOT NULL DEFAULT gen_random_uuid();"))
                .contains(Rule.VOLATILE_DEFAULT)
            // A constant default, and now() (STABLE, evaluated once), are metadata-only since PG 11.
            assertThat(flagged(timeout + "ALTER TABLE t ADD COLUMN a BOOLEAN NOT NULL DEFAULT false;")).isEmpty()
            assertThat(flagged(timeout + "ALTER TABLE t ADD COLUMN a TIMESTAMPTZ NOT NULL DEFAULT NOW();")).isEmpty()
        }

        @Test
        fun `should flag a backfill of a hot table and leave small tables alone`() {
            // The shape of V26, which rewrote every message older than a week in one transaction.
            assertThat(flagged("UPDATE messages SET media_url = NULL WHERE server_timestamp < NOW() - INTERVAL '7 days';"))
                .contains(Rule.BULK_DML_ON_HOT_TABLE)
            assertThat(flagged("DELETE FROM public.message_delivery_status WHERE status = 'x';"))
                .contains(Rule.BULK_DML_ON_HOT_TABLE)
            assertThat(flagged("UPDATE bots SET is_active = false;")).isEmpty()
        }

        @Test
        fun `should flag REINDEX VACUUM FULL and CLUSTER`() {
            assertThat(flagged("REINDEX TABLE messages;")).contains(Rule.REINDEX_NOT_CONCURRENT)
            assertThat(flagged("REINDEX TABLE CONCURRENTLY messages;")).isEmpty()
            assertThat(flagged("VACUUM FULL messages;")).contains(Rule.TABLE_REWRITE)
            assertThat(flagged("CLUSTER messages USING idx_a;")).contains(Rule.TABLE_REWRITE)
        }

        @Test
        fun `should ignore keywords inside comments and function bodies`() {
            assertThat(flagged("-- CREATE INDEX idx_a ON t (a);\n/* DROP INDEX x; */ SELECT 1;")).isEmpty()
            assertThat(flagged("CREATE FUNCTION f() RETURNS void AS $$ BEGIN UPDATE messages SET a = 1; END; $$ LANGUAGE plpgsql;"))
                .doesNotContain(Rule.BULK_DML_ON_HOT_TABLE)
        }

        @Test
        fun `should honour a suppression only when it carries a reason`() {
            val sql = "UPDATE messages SET a = 1 WHERE id = '00000000-0000-0000-0000-000000000000';"
            assertThat(flagged("-- migration-lint: allow bulk-dml-on-hot-table single row by primary key\n$sql")).isEmpty()
            assertThat(flagged("-- migration-lint: allow bulk-dml-on-hot-table\n$sql")).contains(Rule.BULK_DML_ON_HOT_TABLE)
        }

        @Test
        fun `should reject the grandfathered V24 shape the policy was written against`() {
            val v24 = "CREATE INDEX IF NOT EXISTS idx_messages_media_id ON messages (media_id) WHERE media_id IS NOT NULL;"
            assertThat(flagged(v24, outsideTransaction = false))
                .contains(Rule.INDEX_NOT_CONCURRENT, Rule.MISSING_LOCK_TIMEOUT)
        }
    }
}
