package com.muhabbet.app.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching], minus the one thing it gets wrong inside a coroutine.
 *
 * `kotlinx.coroutines.CancellationException` is `java.util.concurrent.CancellationException`, which
 * extends `IllegalStateException` — so a plain `catch (e: Exception)` swallows the one signal that
 * does not mean "this failed". It means "whoever was waiting for you is gone".
 *
 * Swallowing it runs the failure path on the way *out* of a screen: a misleading `ERROR … failed to
 * load` in the log, a snackbar for work nobody asked to finish, and — worse — a write to state that
 * is about to be thrown away or, in the two cases that prompted this helper, to state that is *not*
 * thrown away and shows a permanent error (`ActiveCallScreen` flipping its failure banner as the
 * call screen closes; `PollBubble` marking a vote failed because the bubble scrolled out of its
 * `LazyColumn` mid-request).
 *
 * Cancellation is rethrown so the coroutine machinery still sees it; every other [Exception] is
 * captured into a [Result] exactly as a hand-written `catch (e: Exception)` would. `Error` is
 * deliberately *not* caught: this replaces `catch (e: Exception)` blocks and must not widen them.
 *
 * Usage mirrors [runCatching] — the lambda is inlined, so `suspend` calls inside it are fine and so
 * are `suspend` calls in the [Result.onFailure] handler:
 *
 * ```
 * runCatchingCancellable { repository.load() }
 *     .onFailure { e -> Log.e(TAG, "Failed to load", e) }
 * ```
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
