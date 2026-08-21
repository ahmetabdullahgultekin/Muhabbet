package com.muhabbet.app.ui.whatsnew

import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_8_line_1
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_8_line_2
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_8_line_3
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_9_line_1
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_9_line_2
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_9_line_3
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_9_line_4
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_10_line_1
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_10_line_2
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_10_line_3
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_10_line_4
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_10_line_5
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_10_line_6
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_11_line_1
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_11_line_2
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_11_line_3
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_11_line_4
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_11_line_5
import com.muhabbet.composeapp.generated.resources.whatsnew_0_3_11_line_6
import org.jetbrains.compose.resources.StringResource

/**
 * What each released version changed, in the words of someone using the app rather than someone
 * building it.
 *
 * The text is deliberately **not** derived from `CHANGELOG.md`. Parsing it at build time was
 * considered and rejected (#672): that file is written for an engineer — it carries issue numbers,
 * file names, and the reasoning for things that were *not* done — and none of that survives contact
 * with a person who just wants to know why their voice messages behave differently today. Two
 * readers, two texts. The changelog stays the engineering record; this is the user-facing one, and
 * it is written by hand.
 *
 * Only the structure lives in Kotlin. Every word the user reads is a [StringResource], so both
 * locales are covered and `verifyStringResourceSync` fails the build if one of them is not.
 */
data class ReleaseNote(
    /** Must match `BuildInfo.VERSION` for the release exactly — it is how the sheet finds itself. */
    val version: String,
    val lines: List<StringResource>
)

/**
 * Newest first. The order is the order the release-notes screen shows, and the head of the list is
 * the version whose notes the update sheet is expected to find.
 *
 * Three to six lines each, on purpose. A user reading an update sheet is doing so on the way to
 * something else; a list long enough to need scrolling is a list nobody finishes. What did not make
 * the cut goes in `CHANGELOG.md`, which is not size-limited because nobody reads it under time
 * pressure.
 */
val AppReleaseNotes: List<ReleaseNote> = listOf(
    ReleaseNote(
        version = "0.3.11",
        lines = listOf(
            Res.string.whatsnew_0_3_11_line_1,
            Res.string.whatsnew_0_3_11_line_2,
            Res.string.whatsnew_0_3_11_line_3,
            Res.string.whatsnew_0_3_11_line_4,
            Res.string.whatsnew_0_3_11_line_5,
            Res.string.whatsnew_0_3_11_line_6
        )
    ),
    ReleaseNote(
        version = "0.3.10",
        lines = listOf(
            Res.string.whatsnew_0_3_10_line_1,
            Res.string.whatsnew_0_3_10_line_2,
            Res.string.whatsnew_0_3_10_line_3,
            Res.string.whatsnew_0_3_10_line_4,
            Res.string.whatsnew_0_3_10_line_5,
            Res.string.whatsnew_0_3_10_line_6
        )
    ),
    ReleaseNote(
        version = "0.3.9",
        lines = listOf(
            Res.string.whatsnew_0_3_9_line_1,
            Res.string.whatsnew_0_3_9_line_2,
            Res.string.whatsnew_0_3_9_line_3,
            Res.string.whatsnew_0_3_9_line_4
        )
    ),
    ReleaseNote(
        version = "0.3.8",
        lines = listOf(
            Res.string.whatsnew_0_3_8_line_1,
            Res.string.whatsnew_0_3_8_line_2,
            Res.string.whatsnew_0_3_8_line_3
        )
    )
)

/** The notes for [version], or null if that release shipped without any. */
fun releaseNoteFor(version: String, notes: List<ReleaseNote> = AppReleaseNotes): ReleaseNote? =
    notes.firstOrNull { it.version == version }

/**
 * Whether the update sheet is due this launch.
 *
 * Three conditions, and the order they are written in is the order they matter:
 *
 * 1. **[hasNotes]** — a version nobody wrote notes for gets no sheet. An empty sheet saying "here
 *    is what changed" and then listing nothing is worse than silence, and this is the failure mode
 *    that arrives by itself, the first time someone bumps the version and forgets. (A test asserts
 *    the shipping version always has notes, so this branch should stay theoretical.)
 * 2. **[lastSeenVersion] is not null** — a fresh install never shows it. Telling a brand-new user
 *    what is new since a version they have never run is meaningless, and worse than meaningless
 *    when half the lines describe fixes to bugs they will never know existed. This is also the
 *    fail-safe direction: if the seeding in `RootComponent` ever broke, the visible consequence is
 *    that nobody sees the sheet, not that every new user is handed a changelog on first launch.
 * 3. **the version actually moved** — otherwise it would reopen on every launch, which is how a
 *    once-per-update message becomes something people learn to dismiss without reading.
 *
 * Pure and public so all of it is testable without a device; the round trip through storage is
 * covered in `WhatsNewTest`.
 */
fun shouldShowWhatsNew(
    lastSeenVersion: String?,
    currentVersion: String,
    hasNotes: Boolean
): Boolean = hasNotes && lastSeenVersion != null && lastSeenVersion != currentVersion

/**
 * The version to record on a device that has never recorded one, so that [shouldShowWhatsNew] can
 * tell a fresh install apart from an upgrade.
 *
 * A null `lastSeenVersion` has two causes and they want opposite answers: a genuinely fresh install
 * (say nothing) and an upgrade from a build that predates this feature (say what changed). Nothing
 * distinguishes them by itself — but the test-build notice has been recording the last version this
 * person ran and acknowledged since #519, and that record survives an update. Where it exists, the
 * device has run an earlier build and its owner is owed the notes for this one.
 *
 * Absent it, the current version: a device with no history is a new device.
 *
 * The coupling is deliberate and it expires cleanly. When the test-build notice goes away — it is a
 * statement about the app being pre-release, so one day it will — every device that could reach this
 * function will long since have recorded a version of its own, and this branch will be dead code
 * rather than a broken dependency.
 */
fun versionToRecordOnFirstLaunch(
    acknowledgedTestBuildVersion: String?,
    currentVersion: String
): String = acknowledgedTestBuildVersion ?: currentVersion
