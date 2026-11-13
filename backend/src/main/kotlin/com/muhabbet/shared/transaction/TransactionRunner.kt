package com.muhabbet.shared.transaction

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
 *
 * **It is the only transaction model in the domain (ADR 0014, #893).** No domain service carries
 * `@Transactional`; every service that needs a boundary takes this port and states it. Beyond the
 * send path's reason there are two more: a `@Transactional` method is silently non-transactional
 * when reached through `this` (the proxy is bypassed — #402 was that bug), and it works at all only
 * if the class and method are `open` so CGLIB can subclass them. A boundary written as a call
 * cannot be bypassed and needs neither. It lives in the backend `shared` package rather than in one
 * module because auth, messaging and moderation all need it, and a module importing another
 * module's domain port is the coupling the module rules forbid.
 */
interface TransactionRunner {

    /**
     * Executes [block] in a read-write transaction and returns its result. An exception thrown by
     * [block] rolls the transaction back and propagates, so a caller that fans out after this call
     * never fans out for work that did not commit.
     *
     * Propagation is `REQUIRED`: called inside another transaction, the block joins it. That is the
     * right answer for a service method composed of others, and the wrong one for per-item
     * isolation in a batch — there, nothing may hold a transaction around the loop (#560).
     */
    fun <T> inTransaction(block: () -> T): T

    /**
     * Executes [block] in a read-only transaction: the same boundary as [inTransaction], plus the
     * hint that lets Hibernate skip dirty checking and the driver mark the connection read-only.
     *
     * The default runs an ordinary transaction, because read-only is an optimisation and never a
     * correctness property a caller may rely on, so a test double need not model it. The Spring
     * adapter overrides it.
     */
    fun <T> inReadOnlyTransaction(block: () -> T): T = inTransaction(block)
}
