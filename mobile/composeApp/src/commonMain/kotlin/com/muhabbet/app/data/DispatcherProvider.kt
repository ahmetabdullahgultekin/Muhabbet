package com.muhabbet.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Which thread blocking data-layer work runs on, injected rather than assumed (#820).
 *
 * The repositories called the SQLDelight cache — a synchronous `executeAsList()`, a transaction of
 * twenty upserts each encoding participants to JSON — straight from suspend functions that screens
 * invoke from Compose scopes. A suspend function does not move off the caller's thread by itself,
 * so all of it ran on Main, dropping frames while the list or a chat page loaded.
 *
 * The contract now is **main-safety**: a repository function may be called from any thread and
 * moves its own blocking work onto [io]. The dispatcher is a constructor parameter so a test can
 * substitute one and assert the cache was touched there, which is the only way to keep the rule
 * from quietly eroding.
 *
 * This is the one place in `data/` allowed to name `Dispatchers.*`.
 */
interface DispatcherProvider {
    /** Disk and database I/O. */
    val io: CoroutineDispatcher

    /** CPU-bound work: parsing, compression. */
    val default: CoroutineDispatcher
}

object DefaultDispatcherProvider : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val default: CoroutineDispatcher = Dispatchers.Default
}
