package com.muhabbet.shared.transaction

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
 * it rather than opening a second connection.
 */
@Component
class SpringTransactionRunner(transactionManager: PlatformTransactionManager) : TransactionRunner {

    private val readWrite = TransactionTemplate(transactionManager)
    private val readOnly = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun <T> inTransaction(block: () -> T): T = run(readWrite, block)

    override fun <T> inReadOnlyTransaction(block: () -> T): T = run(readOnly, block)

    /**
     * `executeWithoutResult` and a captured value rather than `execute`, because `execute` uses
     * `null` to mean "no result" and a block may legitimately return `null` — a lookup that found
     * nothing. The holder keeps the two apart without binding [T] to a non-null type. An exception
     * from [block] rolls back and propagates before the holder is read.
     */
    private fun <T> run(template: TransactionTemplate, block: () -> T): T {
        var result: Result<T>? = null
        template.executeWithoutResult { result = Result.success(block()) }
        return checkNotNull(result) { "Transaction callback did not run" }.getOrThrow()
    }
}
