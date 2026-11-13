package com.muhabbet.architecture

/**
 * The online-migration policy of docs/adr/0015-online-safe-migrations.md, as code (#921).
 *
 * Every rule here names a statement that takes a lock writers queue behind, or rewrites a table,
 * for as long as the table is large — which on `messages` means a deploy that stops message
 * sending. They are all invisible at today's row counts, and that is the reason for a lint rather
 * than a review checklist: nothing about running the migration locally, or in CI against an empty
 * Testcontainers database, would ever show one of them being slow.
 *
 * In-repo rather than squawk: squawk is a binary the single self-hosted runner would download on
 * every job, and the parts of it this project needs are a few patterns. This runs inside
 * `:backend:test`, which CI already runs and which needs neither network nor Docker.
 *
 * It is a lexical check over statements, not a SQL parser, and it errs towards flagging. A
 * statement it flags wrongly is suppressed in the migration itself, with a reason a reviewer can
 * read: `-- migration-lint: allow <rule-id> <reason>`.
 */
object OnlineMigrationLint {

    data class Violation(val file: String, val rule: Rule, val statement: String) {
        override fun toString(): String = "$file: [${rule.id}] ${rule.advice}\n    in: ${statement.take(STATEMENT_PREVIEW)}"
    }

    enum class Rule(val id: String, val advice: String) {
        INDEX_NOT_CONCURRENT(
            "index-not-concurrent",
            "CREATE INDEX takes a SHARE lock that blocks every insert for the whole build. Use CREATE INDEX CONCURRENTLY."
        ),
        DROP_INDEX_NOT_CONCURRENT(
            "drop-index-not-concurrent",
            "DROP INDEX takes an ACCESS EXCLUSIVE lock on the table. Use DROP INDEX CONCURRENTLY."
        ),
        CONCURRENTLY_IN_TRANSACTION(
            "concurrently-in-transaction",
            "CONCURRENTLY cannot run in a transaction. Add a <script>.sql.conf beside the migration containing executeInTransaction=false."
        ),
        MISSING_LOCK_TIMEOUT(
            "missing-lock-timeout",
            "A migration that takes table locks must start with SET lock_timeout, " +
                "so a busy table fails the deploy instead of queueing every writer behind it."
        ),
        CONSTRAINT_NOT_VALID(
            "constraint-without-not-valid",
            "ADD CONSTRAINT ... FOREIGN KEY/CHECK scans the table under lock. Add it NOT VALID, then VALIDATE CONSTRAINT in a later migration."
        ),
        UNIQUE_CONSTRAINT_BUILDS_INDEX(
            "unique-constraint-builds-index",
            "ADD UNIQUE/PRIMARY KEY builds its index under lock. CREATE UNIQUE INDEX CONCURRENTLY first, then ADD CONSTRAINT ... USING INDEX."
        ),
        COLUMN_TYPE_CHANGE(
            "column-type-change",
            "ALTER COLUMN ... TYPE rewrites the table under ACCESS EXCLUSIVE. Expand (new column) → migrate → contract instead."
        ),
        SET_NOT_NULL(
            "set-not-null",
            "SET NOT NULL scans the table under ACCESS EXCLUSIVE. Add CHECK (col IS NOT NULL) NOT VALID, validate it, then SET NOT NULL."
        ),
        VOLATILE_DEFAULT(
            "volatile-default",
            "ADD COLUMN with a volatile DEFAULT rewrites every row. Add the column without it, backfill in batches, then set the default."
        ),
        BULK_DML_ON_HOT_TABLE(
            "bulk-dml-on-hot-table",
            "A data backfill of a hot table inside a schema migration rewrites it in one transaction. Do it as a batched background job."
        ),
        REINDEX_NOT_CONCURRENT(
            "reindex-not-concurrent",
            "REINDEX locks out writes. Use REINDEX ... CONCURRENTLY."
        ),
        TABLE_REWRITE(
            "table-rewrite",
            "VACUUM FULL and CLUSTER rewrite the table under ACCESS EXCLUSIVE."
        )
    }

    /** Tables where a whole-table statement is a production incident rather than a pause. */
    val HOT_TABLES = setOf("messages", "message_delivery_status", "conversation_members", "media_files")

    private const val STATEMENT_PREVIEW = 160
    private const val MIN_REASON_LENGTH = 10

    private val VOLATILE_FUNCTIONS = listOf("GEN_RANDOM_UUID(", "RANDOM(", "CLOCK_TIMESTAMP(", "UUID_GENERATE_V4(", "TIMEOFDAY(")
    private val SUPPRESSION = Regex("""--[ \t]*migration-lint:[ \t]*allow[ \t]+([a-z-]+)[ \t]*([^\n]*)""")
    private val LOCKING_DDL = Regex("""\b(ALTER TABLE|CREATE (UNIQUE )?INDEX|DROP INDEX|DROP TABLE|REFERENCES|CREATE TRIGGER|DROP TRIGGER)\b""")
    private val CREATE_INDEX = Regex("""^CREATE (UNIQUE )?INDEX\b""")
    private val HOT_DML = Regex("""^(UPDATE|DELETE FROM)\s+(ONLY\s+)?("?[a-z_]+"?\.)?"?([a-z_]+)"?\b""", RegexOption.IGNORE_CASE)

    /**
     * Checks one migration. [executesOutsideTransaction] is whether its `.sql.conf` sets
     * `executeInTransaction=false`.
     */
    fun lint(fileName: String, sql: String, executesOutsideTransaction: Boolean): List<Violation> {
        val suppressed = suppressions(sql)
        val statements = statements(stripComments(sql))
        val normalized = statements.map { it.uppercase() }
        val violations = mutableListOf<Violation>()

        fun flag(rule: Rule, statement: String) {
            if (rule.id !in suppressed) violations += Violation(fileName, rule, statement)
        }

        normalized.forEachIndexed { i, s -> lintStatement(s, statements[i]).forEach { flag(it, statements[i]) } }

        val usesConcurrently = normalized.any { it.contains(" CONCURRENTLY ") }
        if (usesConcurrently && !executesOutsideTransaction) {
            flag(Rule.CONCURRENTLY_IN_TRANSACTION, statements.first { it.uppercase().contains(" CONCURRENTLY ") })
        }

        val locking = normalized.indexOfFirst { LOCKING_DDL.containsMatchIn(it) }
        val timeout = normalized.indexOfFirst { it.startsWith("SET LOCK_TIMEOUT") || it.startsWith("SET LOCAL LOCK_TIMEOUT") }
        if (locking >= 0 && (timeout < 0 || timeout > locking)) flag(Rule.MISSING_LOCK_TIMEOUT, statements[locking])

        return violations
    }

    /** The rules that look at one statement alone; [s] is [original] upper-cased. */
    private fun lintStatement(s: String, original: String): List<Rule> = buildList {
        if (CREATE_INDEX.containsMatchIn(s) && !s.contains(" CONCURRENTLY ")) add(Rule.INDEX_NOT_CONCURRENT)
        if (s.startsWith("DROP INDEX") && !s.startsWith("DROP INDEX CONCURRENTLY")) add(Rule.DROP_INDEX_NOT_CONCURRENT)
        if (s.startsWith("REINDEX") && !s.contains(" CONCURRENTLY ")) add(Rule.REINDEX_NOT_CONCURRENT)
        if (s.startsWith("VACUUM FULL") || s.startsWith("CLUSTER")) add(Rule.TABLE_REWRITE)
        if (s.startsWith("ALTER TABLE")) addAll(lintAlterTable(s))
        val dmlTarget = HOT_DML.find(original)?.groupValues?.get(4)?.lowercase()
        if (dmlTarget in HOT_TABLES) add(Rule.BULK_DML_ON_HOT_TABLE)
    }

    private fun lintAlterTable(s: String): List<Rule> = buildList {
        val addsCheckedConstraint = Regex("""ADD (CONSTRAINT \S+ )?(FOREIGN KEY|CHECK)\b""").containsMatchIn(s)
        if (addsCheckedConstraint && !s.contains("NOT VALID")) add(Rule.CONSTRAINT_NOT_VALID)
        if (Regex("""ADD (CONSTRAINT \S+ )?(UNIQUE|PRIMARY KEY)\b""").containsMatchIn(s) && !s.contains("USING INDEX")) {
            add(Rule.UNIQUE_CONSTRAINT_BUILDS_INDEX)
        }
        if (Regex("""ALTER (COLUMN )?\S+ (SET DATA )?TYPE\b""").containsMatchIn(s)) add(Rule.COLUMN_TYPE_CHANGE)
        if (Regex("""ALTER (COLUMN )?\S+ SET NOT NULL\b""").containsMatchIn(s)) add(Rule.SET_NOT_NULL)
        if (s.contains("ADD COLUMN") && s.contains("DEFAULT") && VOLATILE_FUNCTIONS.any { s.contains(it) }) {
            add(Rule.VOLATILE_DEFAULT)
        }
    }

    /** Rule ids suppressed by `-- migration-lint: allow <rule> <reason>`; a suppression with no real reason is ignored. */
    private fun suppressions(sql: String): Set<String> =
        SUPPRESSION.findAll(sql)
            .filter { it.groupValues[2].trim().length >= MIN_REASON_LENGTH }
            .map { it.groupValues[1] }
            .toSet()

    private fun stripComments(sql: String): String =
        sql.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { line -> line.substringBefore("--") }

    /**
     * Splits on `;`, except inside a `$$`-quoted body (a function or DO block), and collapses
     * whitespace so the patterns above can assume single spaces. A trailing space is added so a
     * keyword at the end of a statement still matches ` KEYWORD `.
     */
    private fun statements(sql: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inDollarQuote = false
        var i = 0
        while (i < sql.length) {
            if (sql.startsWith("$$", i)) {
                inDollarQuote = !inDollarQuote
                current.append("$$")
                i += 2
                continue
            }
            val c = sql[i]
            if (c == ';' && !inDollarQuote) {
                result += current.toString()
                current.clear()
            } else {
                current.append(c)
            }
            i++
        }
        result += current.toString()
        return result.map { it.replace(Regex("""\s+"""), " ").trim() + " " }.filter { it.isNotBlank() }
    }
}
