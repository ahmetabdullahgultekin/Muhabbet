package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.domain.port.out.TransactionRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * The only place [TransactionRunner] meets Spring. Uses [TransactionTemplate] rather than a
 * `@Transactional` method on a helper bean: the template needs no proxy, so it cannot be defeated
 * by a self-invocation — the trap that makes `this.someTransactionalMethod()` silently run with no
 * transaction at all.
 *
 * Propagation is left at the default (`REQUIRED`), so a caller that already has a transaction joins
 * it rather than opening a second connection. Nothing on the send path does, but a future batch job
 * might, and joining is the safe answer for both.
 */
@Component
class SpringTransactionRunner(transactionManager: PlatformTransactionManager) : TransactionRunner {

    private val template = TransactionTemplate(transactionManager)

    override fun <T : Any> inTransaction(block: () -> T): T =
        // Non-null by the port's contract: `execute` returns null only when the callback does, and
        // the callback is `block`, whose type forbids it.
        template.execute { block() } ?: error("Transaction returned no result")
}
