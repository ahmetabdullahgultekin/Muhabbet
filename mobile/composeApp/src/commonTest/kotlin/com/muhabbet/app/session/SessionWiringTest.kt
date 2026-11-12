package com.muhabbet.app.session

import com.muhabbet.app.data.local.FakeTokenStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The session wiring #349 is about, from the side that can be exercised without an emulator.
 *
 * #349's four named symptoms are about the *key* on a Compose effect, and #540 fixed those and
 * verified them on a device. What no test could reach — and what nothing cancelled — is the other
 * direction: a session leaves two things behind that outlive the composition, and until this class
 * existed the app had an on switch for both and an off switch for neither.
 *
 * These tests drive the transitions a real device makes, in order, and assert on what the app leaves
 * running afterwards.
 */
class SessionWiringTest {

    private class RecordingSync : BackgroundSync {
        var scheduled = 0
            private set
        var cancelled = 0
            private set

        override fun schedule() { scheduled++ }
        override fun cancel() { cancelled++ }
    }

    private class RecordingIdentity : CrashIdentity {
        /** The last id attached, or null if it was detached (or never attached). */
        var attached: String? = null
            private set
        var detachCount = 0
            private set

        override fun attach(userId: String) { attached = userId }
        override fun detach() {
            attached = null
            detachCount++
        }
    }

    private class RecordingWipe : LocalDataWipe {
        var wipes = 0
            private set

        override suspend fun wipe() { wipes++ }
    }

    private class Device {
        val storage = FakeTokenStorage()
        val sync = RecordingSync()
        val identity = RecordingIdentity()
        val dataWipe = RecordingWipe()
        val wiring = SessionWiring(storage, sync, identity, dataWipe)

        fun signIn(userId: String) = storage.saveTokens("access", "refresh", userId, "device-1")
        fun signOut() = storage.clear()
    }

    // ─── The session starting ───

    @Test
    fun `should attach the user who signed in during this session, not the snapshot taken before login`() = runTest {
        // The shape of #349: on a fresh install the first composition happens on the LOGIN screen,
        // where getUserId() is null. Reading it there and never again is exactly what left a whole
        // session's crash reports unattributed.
        val device = Device()

        device.wiring.onSessionEnded()
        assertNull(device.identity.attached, "nobody is signed in on the login screen")

        device.signIn("user-42")
        device.wiring.onSessionActive()

        assertEquals("user-42", device.identity.attached)
    }

    @Test
    fun `should schedule background sync when a session becomes active`() {
        val device = Device().apply { signIn("user-42") }

        device.wiring.onSessionActive()

        assertEquals(1, device.sync.scheduled)
    }

    // ─── The session ending — the half nothing did ───

    @Test
    fun `should cancel background sync when the session ends`() = runTest {
        // enqueueUniquePeriodicWork writes into WorkManager's own database, so the job survives the
        // composition, the Activity, process death and a reboot. With no cancel, a device that had
        // logged in once kept waking every 15 minutes for as long as the app stayed installed.
        val device = Device().apply { signIn("user-42") }
        device.wiring.onSessionActive()

        device.signOut()
        device.wiring.onSessionEnded()

        assertEquals(1, device.sync.cancelled, "logging out must stop the periodic sync job")
    }

    @Test
    fun `should detach the crash identity when the session ends`() = runTest {
        // Sentry's user is global mutable state in the SDK, and on iOS it is a value in
        // NSUserDefaults that outlives the process. Left attached, every crash report after logout
        // still names the account that logged out — in a privacy-first messenger.
        val device = Device().apply { signIn("user-42") }
        device.wiring.onSessionActive()

        device.signOut()
        device.wiring.onSessionEnded()

        assertNull(device.identity.attached)
    }

    @Test
    fun `should clear what a previous install left behind on a logged-out cold start`() = runTest {
        // The migration path. Every build before this one could leave an enqueued job and an
        // attached user on a device that logged out; this is the only moment the app gets to undo
        // that, and it must not require the user to log in again first.
        val device = Device()

        device.wiring.onSessionEnded()

        assertEquals(1, device.sync.cancelled)
        assertEquals(1, device.identity.detachCount)
        assertEquals(1, device.dataWipe.wipes, "and the message database an earlier logout never erased")
    }

    // ─── Sequences a real device actually performs ───

    @Test
    fun `should hand the second user a session of their own after the first logs out`() = runTest {
        val device = Device()

        device.signIn("user-1")
        device.wiring.onSessionActive()
        device.signOut()
        device.wiring.onSessionEnded()
        device.signIn("user-2")
        device.wiring.onSessionActive()

        assertEquals("user-2", device.identity.attached)
        assertEquals(2, device.sync.scheduled)
        assertEquals(1, device.sync.cancelled)
    }

    @Test
    fun `should stay scheduled across an Activity recreation`() {
        // The language switch recreates the Activity on purpose (#511), so the effect re-runs with
        // loggedIn still true. Re-scheduling is safe — enqueueUniquePeriodicWork uses KEEP — but
        // nothing may be cancelled on the way through, or the switch would silently disable sync.
        val device = Device().apply { signIn("user-42") }

        device.wiring.onSessionActive()
        device.wiring.onSessionActive()

        assertEquals(0, device.sync.cancelled)
        assertEquals("user-42", device.identity.attached)
    }

    // ─── The data a session leaves on disk (#752) ───

    @Test
    fun `should wipe the local message data when the session ends`() = runTest {
        // Logout used to clear the tokens and nothing else: every conversation, message and unsent
        // outbox row stayed in SQLite, and the next account to sign in on the phone could see them.
        val device = Device().apply { signIn("user-42") }
        device.wiring.onSessionActive()

        device.signOut()
        device.wiring.onSessionEnded()

        assertEquals(1, device.dataWipe.wipes)
    }

    @Test
    fun `should not wipe anything when a session becomes active`() {
        // An Activity recreation re-runs the effect with loggedIn still true; wiping there would
        // throw away the cache of the user who is still signed in.
        val device = Device().apply { signIn("user-42") }

        device.wiring.onSessionActive()
        device.wiring.onSessionActive()

        assertEquals(0, device.dataWipe.wipes)
    }

    @Test
    fun `should still cancel sync and detach the identity when the wipe fails`() = runTest {
        val storage = FakeTokenStorage().apply { saveTokens("a", "r", "user-42", "d") }
        val sync = RecordingSync()
        val identity = RecordingIdentity()
        val wiring = SessionWiring(storage, sync, identity) { throw IllegalStateException("database locked") }
        wiring.onSessionActive()

        wiring.onSessionEnded()

        assertEquals(1, sync.cancelled)
        assertNull(identity.attached)
    }

    // ─── Neither call site can show anyone an error ───

    @Test
    fun `should not throw when the platform refuses`() = runTest {
        // WorkManager.getInstance throws if the library was never initialised. This runs inside a
        // LaunchedEffect, where an escaping exception takes the app down — at login, of all moments.
        val throwingSync = object : BackgroundSync {
            override fun schedule(): Unit = throw IllegalStateException("WorkManager not initialised")
            override fun cancel(): Unit = throw IllegalStateException("WorkManager not initialised")
        }
        val identity = RecordingIdentity()
        val storage = FakeTokenStorage().apply { saveTokens("a", "r", "user-42", "d") }
        val wiring = SessionWiring(storage, throwingSync, identity) { throw IllegalStateException("database locked") }

        wiring.onSessionActive()
        wiring.onSessionEnded()

        assertTrue(true, "reaching this line is the assertion: neither call escaped")
    }
}
