package com.muhabbet.auth.adapter.out.persistence

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Instant
import java.util.UUID

/**
 * V28 + V29 (#908) run against a database that already holds the bad data.
 *
 * A Spring test cannot do this: Flyway runs at context start on an empty schema, so the clean-up
 * would have nothing to clean. Here the schema is migrated to V27, production's shape before the
 * fix is seeded — one token on three accounts' devices, a blank token, a revoked companion still
 * holding a token — and then the rest of the migrations run, exactly as the deploy will.
 */
@Testcontainers
class PushTokenUniquenessMigrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("muhabbet_migration_test")
            withUsername("muhabbet")
            withPassword("muhabbet_test")
        }
    }

    private fun flyway(target: MigrationVersion) = Flyway.configure()
        .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        .locations("classpath:db/migration")
        .target(target)
        // As application.yml sets it: with the default transactional lock, V29's CONCURRENTLY
        // build waits on Flyway's own open transaction and never finishes.
        .configuration(mapOf("flyway.postgresql.transactional.lock" to "false"))
        .load()

    private fun connection(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun Connection.user(): UUID {
        val id = UUID.randomUUID()
        prepareStatement("INSERT INTO users (id, phone_number) VALUES (?, ?)").use {
            it.setObject(1, id)
            it.setString(2, "+90500" + (1000000 + (0..8999999).random()))
            it.executeUpdate()
        }
        return id
    }

    private fun Connection.device(
        token: String?,
        lastActiveAt: Instant?,
        revokedAt: Instant? = null
    ): UUID {
        val id = UUID.randomUUID()
        prepareStatement(
            "INSERT INTO devices (id, user_id, platform, push_token, last_active_at, revoked_at) VALUES (?, ?, 'android', ?, ?, ?)"
        ).use {
            it.setObject(1, id)
            it.setObject(2, user())
            it.setString(3, token)
            it.setObject(4, lastActiveAt?.let { t -> java.sql.Timestamp.from(t) })
            it.setObject(5, revokedAt?.let { t -> java.sql.Timestamp.from(t) })
            it.executeUpdate()
        }
        return id
    }

    private fun Connection.tokenOf(deviceId: UUID): String? =
        prepareStatement("SELECT push_token FROM devices WHERE id = ?").use {
            it.setObject(1, deviceId)
            it.executeQuery().use { rs -> rs.next(); rs.getString(1) }
        }

    /** (indisunique, indisvalid) of the named index, or null if it does not exist. */
    private fun Connection.uniqueAndValid(indexName: String): Pair<Boolean, Boolean>? =
        prepareStatement(
            """
            SELECT ix.indisunique, ix.indisvalid
            FROM pg_index ix JOIN pg_class c ON c.oid = ix.indexrelid
            WHERE c.relname = ?
            """.trimIndent()
        ).use {
            it.setString(1, indexName)
            it.executeQuery().use { rs -> if (rs.next()) rs.getBoolean(1) to rs.getBoolean(2) else null }
        }

    private fun Connection.setToken(deviceId: UUID, token: String): Int =
        prepareStatement("UPDATE devices SET push_token = ? WHERE id = ?").use {
            it.setString(1, token)
            it.setObject(2, deviceId)
            it.executeUpdate()
        }

    @Test
    fun `should leave each token on its most recently active device and then enforce one holder`() {
        flyway(MigrationVersion.fromVersion("27")).migrate()

        val now = Instant.parse("2026-11-01T12:00:00Z")
        connection().use { db ->
            // Three accounts that each signed in on the same phone, most recent last.
            val oldest = db.device("phone-token", lastActiveAt = now.minusSeconds(7200))
            val neverActive = db.device("phone-token", lastActiveAt = null)
            val newest = db.device("phone-token", lastActiveAt = now)
            val untouched = db.device("own-token", lastActiveAt = now.minusSeconds(60))
            val blank = db.device("   ", lastActiveAt = now)
            val otherBlank = db.device("", lastActiveAt = now)
            val revoked = db.device("revoked-token", lastActiveAt = now, revokedAt = now)

            flyway(MigrationVersion.LATEST).migrate()

            assertEquals("phone-token", db.tokenOf(newest), "the account on the phone now lost its token")
            assertNull(db.tokenOf(oldest), "an earlier account kept the phone's token")
            assertNull(db.tokenOf(neverActive))
            assertEquals("own-token", db.tokenOf(untouched), "a token with one holder was touched")
            assertNull(db.tokenOf(blank))
            assertNull(db.tokenOf(otherBlank))
            assertNull(db.tokenOf(revoked), "a revoked companion kept a delivery address")

            assertEquals(
                true to true,
                db.uniqueAndValid("uq_devices_push_token"),
                "uq_devices_push_token must exist, be unique and be valid"
            )

            // And it bites: a second holder is refused by the database itself.
            val ex = assertThrows<SQLException> { db.setToken(oldest, "phone-token") }
            assertEquals("23505", ex.sqlState, "expected a unique violation, got: ${ex.message}")

            // Many devices with no token are still fine — NULLs are not a shared value. Throws if not.
            db.device(null, lastActiveAt = now)
            db.device(null, lastActiveAt = now)
        }
    }
}
