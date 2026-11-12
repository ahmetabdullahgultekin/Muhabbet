package com.muhabbet.app.session

import com.muhabbet.app.data.local.SessionDataCache
import com.muhabbet.app.data.local.SessionDataGate
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * Erases what a signed-in session left on the device, as [SessionWiring] needs it (#752).
 *
 * This is the 0.4 slice of ADR 0020's `SessionTeardown`: no crypto yet — the database is still
 * plaintext SQLite, and erasing it means deleting its rows. SQLCipher and crypto-erase arrive in
 * 0.5 behind this same seam.
 */
fun interface LocalDataWipe {
    suspend fun wipe()
}

/**
 * [LocalDataWipe] as the app performs it: the cached conversations and messages, the unsent outbox,
 * and the transport's in-memory queues.
 *
 * Each step runs whether or not the one before it failed (ADR 0020 §3: a failure in one does not
 * stop later ones). A wipe that stops at the first error leaves exactly the data the user believed
 * gone, and the caller can do nothing about it — it runs from a Compose effect on the way out.
 *
 * Runs to completion once started, whatever happens to the caller. It is called from a Compose
 * effect that an Activity recreation or a quick re-login can cancel, and a wipe abandoned halfway
 * leaves exactly the data the user believed gone.
 *
 * @param gate shared with every writer of session data, so a write already under way finishes
 *   before the erase and a write that arrives after it is dropped rather than resurrecting a row.
 * @param clearTransportState `WsClient.clearSessionState` in production. A function rather than the
 *   client so this class does not need a socket to be constructed in a test.
 * @param storageContext where the SQLite deletes run; tests pass `EmptyCoroutineContext`.
 */
class DeviceLocalDataWipe(
    private val cache: SessionDataCache,
    private val gate: SessionDataGate,
    private val clearTransportState: suspend () -> Unit,
    private val storageContext: CoroutineContext = Dispatchers.Default
) : LocalDataWipe {

    override suspend fun wipe() = withContext(NonCancellable) {
        runCatchingCancellable { gate.wipe { withContext(storageContext) { cache.clearSessionData() } } }
            .onFailure { e -> Log.e(TAG, "Could not erase the local message cache", e) }
        runCatchingCancellable { clearTransportState() }
            .onFailure { e -> Log.e(TAG, "Could not clear the transport's in-memory queues", e) }
        Unit
    }

    private companion object {
        const val TAG = "LocalDataWipe"
    }
}
