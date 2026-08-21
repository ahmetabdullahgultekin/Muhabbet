package com.muhabbet.app.session

import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.BackgroundSyncManager
import com.muhabbet.app.platform.CrashReporter
import com.muhabbet.app.util.Log

/**
 * The two things a signed-in session leaves **outside** the composition, and therefore the two that
 * have to be switched off by hand when it ends.
 *
 * `App.kt` wires four other session concerns — the WebSocket, the global DELIVERED ack pump, push
 * token registration and E2E key registration — directly in effects keyed on `loggedIn` (#349,
 * #454). Those need nothing here: each one dies with the composition or with the process. These two
 * do not.
 *
 * - **The background sync job** is written into WorkManager's own database by
 *   `enqueueUniquePeriodicWork`. It survives the composition, the Activity, process death and a
 *   reboot. Nothing in the app ever called [BackgroundSync.cancel] — the whole repository had zero
 *   call sites for it — so once a device had logged in it kept waking every 15 minutes for as long
 *   as the app stayed installed, logging a `Result.failure()` nobody sees because `getUserId()` is
 *   null.
 * - **The crash reporter's user** is global mutable state inside the Sentry SDK (and, on iOS, a
 *   value in `NSUserDefaults` that outlives the process). It was attached once, from a snapshot read
 *   before the login screen had even run, and never detached — so a session-login had no identity on
 *   any crash report, and after logout every report still carried the id of the user who left.
 *
 * Both halves are the same defect #349 describes, in the same file, and #540 fixed four of the six
 * effects and said so plainly of the fifth: *"`setUser` has the same defect … flagged rather than
 * done."* This is that, plus the off-switch the sixth never had.
 *
 * Deliberately a plain class rather than more Compose effects: it is called from exactly one
 * `LaunchedEffect(loggedIn)` in `App.kt`, and being ordinary Kotlin is what makes the logout
 * direction testable at all on a host with no emulator — the same reasoning that pulled
 * `PushTokenRegistrar` out of the composition for #398.
 */
class SessionWiring(
    private val tokenStorage: TokenStorage,
    private val backgroundSync: BackgroundSync,
    private val crashIdentity: CrashIdentity
) {

    /**
     * A session is now on screen — whether the user just signed in or the app started already
     * authenticated. Both arrive here identically, which is the entire point of #349.
     */
    fun onSessionActive() {
        // Read now, not at first composition: on a fresh install that snapshot is null, because the
        // login screen has not run yet.
        tokenStorage.getUserId()?.let { userId ->
            runGuarded("attach the crash identity") { crashIdentity.attach(userId) }
        }
        runGuarded("schedule background sync") { backgroundSync.schedule() }
    }

    /**
     * No session — the user logged out, or the app started on the login screen.
     *
     * Also runs on a logged-out cold start, on purpose: that is what finally clears the orphaned
     * WorkManager job and the stale crash identity left behind by every build before this one, on
     * devices that logged out while nothing cancelled either.
     */
    fun onSessionEnded() {
        runGuarded("detach the crash identity") { crashIdentity.detach() }
        runGuarded("cancel background sync") { backgroundSync.cancel() }
    }

    /**
     * Neither call site can show a user anything, and both run from a Compose effect where an
     * escaping exception takes the app down — at login, of all moments. `WorkManager.getInstance`
     * throws if the library was never initialised, so this is not hypothetical. Absorbed, never
     * silent (#264).
     */
    private inline fun runGuarded(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "Session wiring failed to $what: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "SessionWiring"
    }
}

/**
 * The periodic background message sync, as [SessionWiring] needs it: an on switch and an off switch.
 *
 * An interface rather than [BackgroundSyncManager] itself because that is an `expect class` needing
 * an Android `Context`, so naming it directly would put the off switch permanently out of reach of
 * `commonTest` — the same trap `WsClient` documents for `PendingMessageCache`, where a test ended up
 * asserting that a message had been queued by a client with nowhere to queue it.
 */
interface BackgroundSync {
    fun schedule()
    fun cancel()
}

/** [BackgroundSync] as the app actually performs it. */
class PlatformBackgroundSync(
    private val syncManager: BackgroundSyncManager
) : BackgroundSync {
    override fun schedule() = syncManager.schedulePeriodicSync()
    override fun cancel() = syncManager.cancelPeriodicSync()
}

/**
 * Who the crash reporter believes is using the app.
 *
 * Narrower than [CrashReporter] on purpose: a session has no business reaching `captureException` or
 * `addBreadcrumb`, and [CrashReporter] is an `expect object`, which no test can substitute.
 */
interface CrashIdentity {
    fun attach(userId: String)
    fun detach()
}

/** [CrashIdentity] as the app actually reports it. */
object PlatformCrashIdentity : CrashIdentity {
    override fun attach(userId: String) = CrashReporter.setUser(userId)
    override fun detach() = CrashReporter.clearUser()
}
