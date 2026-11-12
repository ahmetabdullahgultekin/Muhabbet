package com.muhabbet.app.data.local

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orders writes to the session's local data against the wipe that erases it on logout (#752).
 *
 * Cancelling a writer is not enough. The DELIVERED pump and the push handler write SQLite on a
 * background dispatcher, and blocking SQLite work does not stop when its coroutine is cancelled — so
 * a write already under way when the user logged out could commit a row *after* the wipe, and that
 * row, belonging to the account that left, would sit on disk for the next one. The push handler is
 * not even tied to the session: it runs in a service.
 *
 * So every write names the session it belongs to, and the wipe ends that session:
 * - a writer captures [currentSession] when it starts, and writes through [writeIfCurrent];
 * - [wipe] waits for any write in progress, then starts a new session before it erases;
 * - a write for a session that has ended is dropped, and the caller learns that it was, so it does
 *   not acknowledge a message it no longer holds.
 *
 * One Koin singleton. Two copies would be two locks, which serialise nothing.
 */
class SessionDataGate {
    private val mutex = Mutex()
    private var session = 0L

    suspend fun currentSession(): Long = mutex.withLock { session }

    /** Runs [write] only if [session] is still the current one; null when it has ended. */
    suspend fun <T> writeIfCurrent(session: Long, write: suspend () -> T): T? =
        mutex.withLock { if (session == this.session) write() else null }

    /** Ends the current session — no write for it lands after this — then runs [erase]. */
    suspend fun wipe(erase: suspend () -> Unit) {
        mutex.withLock {
            session++
            erase()
        }
    }
}
