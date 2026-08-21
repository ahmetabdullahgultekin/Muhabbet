package com.muhabbet.shared

import com.muhabbet.messaging.domain.port.out.TransactionRunner

/**
 * Runs the block, and that is the whole of it.
 *
 * A unit test has no transaction manager and does not want one; what it wants is for the block to
 * have run by the time the call returns, which is exactly the contract
 * [TransactionRunner.inTransaction] promises. Written out rather than mocked because a
 * `mockk(relaxed = true)` would return `null` for the generic result and every caller would then
 * fail on something unrelated to what the test is about.
 */
class InlineTransactionRunner : TransactionRunner {
    override fun <T : Any> inTransaction(block: () -> T): T = block()
}

/**
 * A [TransactionRunner] that rolls back: it runs the block and then throws, the way a commit
 * failure would. Lets a test assert that nothing was fanned out for work that did not commit.
 */
class FailingTransactionRunner(private val failure: RuntimeException) : TransactionRunner {
    override fun <T : Any> inTransaction(block: () -> T): T {
        block()
        throw failure
    }
}
