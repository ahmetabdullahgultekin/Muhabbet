package com.muhabbet.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

/**
 * A [DispatcherProvider] whose [io] marks the work it runs, so a test can ask "was this on the I/O
 * dispatcher?" from inside a synchronous cache call that has no coroutine context to inspect (#820).
 *
 * Each dispatcher wraps [delegate] — the test's own scheduler — so everything stays on one thread
 * and deterministic; only the flag differs.
 */
class MarkingDispatcherProvider(delegate: CoroutineDispatcher) : DispatcherProvider {

    /** True exactly while a block dispatched to [io] is running. */
    var insideIo: Boolean = false
        private set

    override val io: CoroutineDispatcher = Marking(delegate) { insideIo = it }
    override val default: CoroutineDispatcher = delegate

    private class Marking(
        private val delegate: CoroutineDispatcher,
        private val mark: (Boolean) -> Unit,
    ) : CoroutineDispatcher() {
        override fun isDispatchNeeded(context: CoroutineContext): Boolean = true

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            delegate.dispatch(context, Runnable {
                mark(true)
                try {
                    block.run()
                } finally {
                    mark(false)
                }
            })
        }
    }
}
