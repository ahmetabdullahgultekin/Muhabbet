package com.muhabbet.app.ui.whatsnew

import com.muhabbet.app.BuildInfo
import com.muhabbet.app.data.local.FakeTokenStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The "shown exactly once, and never to a new user" rule from #672.
 *
 * Every case here is one the acceptance criterion names, plus the two that would silently defeat the
 * feature rather than break it: a version with no notes written for it, and a `TokenStorage` member
 * that does not persist.
 */
class WhatsNewTest {

    // ─── When the sheet is due ──────────────────────────────────────────────

    @Test
    fun `should not show on a fresh install`() {
        // Nothing recorded means this device has no history to be caught up on. Telling a brand-new
        // user what changed since a version they have never run is meaningless — and the same null
        // is what a broken seed would produce, so silence is the right way to fail.
        assertFalse(
            shouldShowWhatsNew(lastSeenVersion = null, currentVersion = "0.3.11", hasNotes = true)
        )
    }

    @Test
    fun `should show after an update`() {
        assertTrue(
            shouldShowWhatsNew(lastSeenVersion = "0.3.10", currentVersion = "0.3.11", hasNotes = true)
        )
    }

    @Test
    fun `should not show again on the same version`() {
        assertFalse(
            shouldShowWhatsNew(lastSeenVersion = "0.3.11", currentVersion = "0.3.11", hasNotes = true)
        )
    }

    @Test
    fun `should not show a version nobody wrote notes for`() {
        // An empty sheet announcing "here is what changed" and then listing nothing is worse than
        // saying nothing at all, and this is the case that arrives by itself the first time someone
        // bumps the version and forgets the strings.
        assertFalse(
            shouldShowWhatsNew(lastSeenVersion = "0.3.10", currentVersion = "0.3.11", hasNotes = false)
        )
    }

    // ─── The round trip through storage ─────────────────────────────────────

    @Test
    fun `should stay dismissed for the rest of this version and return after the next update`() {
        // Guards the trap this interface has now fallen into five times: a member added with a
        // default no-op body that no implementation overrides. If setLastSeenVersion did nothing,
        // the read below would still be null — and because null means "fresh install, say nothing",
        // the sheet would never appear on any device rather than appearing too often. That failure
        // looks exactly like the feature not having been written.
        val storage = FakeTokenStorage()
        storage.setLastSeenVersion("0.3.10")
        assertTrue(shouldShowWhatsNew(storage.getLastSeenVersion(), "0.3.11", hasNotes = true))

        // The user closes the sheet.
        storage.setLastSeenVersion("0.3.11")

        assertEquals("0.3.11", storage.getLastSeenVersion())
        assertFalse(shouldShowWhatsNew(storage.getLastSeenVersion(), "0.3.11", hasNotes = true))
        assertTrue(shouldShowWhatsNew(storage.getLastSeenVersion(), "0.3.12", hasNotes = true))
    }

    // ─── Seeding a device that has never recorded a version ─────────────────

    @Test
    fun `should seed a fresh install with the current version so it sees nothing`() {
        val storage = FakeTokenStorage()
        assertEquals(null, storage.getLastSeenVersion())

        storage.setLastSeenVersion(
            versionToRecordOnFirstLaunch(storage.getTestBuildNoticeAckVersion(), "0.3.11")
        )

        assertEquals("0.3.11", storage.getLastSeenVersion())
        assertFalse(shouldShowWhatsNew(storage.getLastSeenVersion(), "0.3.11", hasNotes = true))
    }

    @Test
    fun `should seed an upgrade with the version it was already running`() {
        // The device has run 0.3.10 and acknowledged its test-build notice, so it is not a fresh
        // install and its owner is owed this release's notes. Without this, the release that
        // introduces the feature would be the one release nobody is told about.
        val storage = FakeTokenStorage()
        storage.setTestBuildNoticeAckVersion("0.3.10")

        storage.setLastSeenVersion(
            versionToRecordOnFirstLaunch(storage.getTestBuildNoticeAckVersion(), "0.3.11")
        )

        assertEquals("0.3.10", storage.getLastSeenVersion())
        assertTrue(shouldShowWhatsNew(storage.getLastSeenVersion(), "0.3.11", hasNotes = true))
    }

    // ─── The notes themselves ───────────────────────────────────────────────

    @Test
    fun `should have notes for the version being shipped`() {
        // This is the owner's release rule as a test: every version that goes out tells the user
        // what changed in it. Bump BuildInfo.VERSION without writing the strings and this fails
        // here rather than in front of a user, where the failure is a sheet that never opens.
        assertNotNull(
            releaseNoteFor(BuildInfo.VERSION),
            "No release notes for ${BuildInfo.VERSION}. Add whatsnew_* strings in both locales " +
                "and an entry in AppReleaseNotes before shipping it."
        )
    }

    @Test
    fun `should list each version once`() {
        val versions = AppReleaseNotes.map { it.version }
        assertEquals(versions.size, versions.toSet().size, "Duplicate version in AppReleaseNotes")
    }

    @Test
    fun `should keep every release between three and six lines`() {
        // Not style policing: a sheet long enough to scroll is a sheet nobody reads to the end, and
        // the whole point of it is that it is read once, on the way to something else.
        AppReleaseNotes.forEach { note ->
            assertTrue(
                note.lines.size in 3..6,
                "${note.version} has ${note.lines.size} lines; keep it between 3 and 6"
            )
        }
    }
}
