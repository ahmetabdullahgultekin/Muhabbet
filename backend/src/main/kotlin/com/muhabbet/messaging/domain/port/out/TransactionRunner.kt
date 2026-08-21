package com.muhabbet.messaging.domain.port.out

/**
 * Runs a block of work inside one database transaction, and returns when that transaction has
 * **committed**.
 *
 * This exists so a service can say where a transaction ends without saying how one is started.
 * `@Transactional` cannot express that: it wraps a whole method, so anything the method does after
 * its last write — a WebSocket fan-out, a push, a Redis publish — is still inside the transaction,
 * holding one of the twenty pool connections for as long as the slowest recipient's socket takes.
 * That was #491, and the ceiling it put on the whole instance was twenty concurrent sends.
 *
 * The obvious alternative, `@TransactionalEventListener(AFTER_COMMIT)`, fixes the *ordering* — no
 * recipient is handed a message the database has not committed — but not the *resource*:
 * `AbstractPlatformTransactionManager.processCommit` triggers the after-commit synchronizations
 * before `cleanupAfterCompletion`, so whether the connection is back in the pool by then depends on
 * Hibernate's `connection.handling_mode`, which this application never sets. A fix for connection
 * exhaustion must not rest on a default we do not configure. Moving the boundary is unconditional:
 * the block commits, the transaction is over, and only then does the caller fan out — on its own
 * thread, so two messages in one conversation cannot swap places the way a pooled executor would
 * let them.
 *
 * A port rather than a `TransactionTemplate` field because the domain does not import Spring, and
 * because the test double is one line — run the block — which is exactly the behaviour a unit test
 * wants.
 */
interface TransactionRunner {

    /**
     * Executes [block] in a transaction and returns its result. An exception thrown by [block]
     * rolls the transaction back and propagates, so a caller that fans out after this call never
     * fans out for work that did not commit.
     *
     * [T] is bound to a non-null type deliberately: Spring's own template signals "no result" with
     * `null`, and a block that may legitimately return `null` would be indistinguishable from it.
     * Callers that have nothing to return should return the outcome they *do* have, which on this
     * path is the saved message and its recipients.
     */
    fun <T : Any> inTransaction(block: () -> T): T
}
