package com.muhabbet.app.ui.settings

import com.muhabbet.designsystem.theme.MuhabbetThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two radio groups in Settings, and the one rule they share: the filled row is derived from what
 * is in effect, never from a constant and never from a hand-written list that can drift (#505).
 */
class SettingsSelectionTest {

    // ── Language ─────────────────────────────

    /**
     * The reported defect, exactly. A fresh install on an `en-US` device has no stored preference,
     * the resource system resolves `values-en/`, and the old code answered with the project default
     * — so the radio said Türkçe while every string on screen was English, and a Turkish user could
     * not select Turkish because it already looked selected.
     */
    @Test
    fun should_select_english_when_nothing_stored_and_resources_resolved_english() {
        assertEquals(AppLanguage.English, selectedLanguage(stored = null, rendered = "en"))
    }

    /**
     * A device locale the app does not translate falls back to `values/`, so the app renders Turkish
     * and the radio must agree. This is the case the old constant happened to get right, and it must
     * keep working.
     */
    @Test
    fun should_select_turkish_when_nothing_stored_and_resources_fell_back_to_default() {
        assertEquals(AppLanguage.Turkish, selectedLanguage(stored = null, rendered = "tr"))
    }

    @Test
    fun should_select_the_stored_preference_when_one_exists() {
        assertEquals(AppLanguage.Turkish, selectedLanguage(stored = "tr", rendered = "tr"))
        assertEquals(AppLanguage.English, selectedLanguage(stored = "en", rendered = "en"))
    }

    /** The preference is what the next launch applies, so it wins if the two ever disagree. */
    @Test
    fun should_prefer_the_stored_language_over_the_rendered_one() {
        assertEquals(AppLanguage.Turkish, selectedLanguage(stored = "tr", rendered = "en"))
    }

    /** User data, so a value the app no longer offers must not leave the group with nothing filled. */
    @Test
    fun should_fall_back_to_the_rendered_language_when_the_stored_one_is_unknown() {
        assertEquals(AppLanguage.English, selectedLanguage(stored = "de", rendered = "en"))
        assertEquals(AppLanguage.Turkish, selectedLanguage(stored = "", rendered = "tr"))
    }

    /**
     * Totality: for every input the picker can see, exactly one row is filled in. The group renders
     * `AppLanguage.entries`, so an answer inside that set is the same statement as "one row is
     * selected".
     */
    @Test
    fun should_always_select_exactly_one_language_row() {
        val stored = listOf(null, "tr", "en", "de", "", "TR")
        val rendered = AppLanguage.entries.map { it.code }
        stored.forEach { s ->
            rendered.forEach { r ->
                val selected = selectedLanguage(stored = s, rendered = r)
                assertEquals(
                    1,
                    AppLanguage.entries.count { it == selected },
                    "stored=$s rendered=$r selected nothing or more than one row"
                )
            }
        }
    }

    // ── Theme ────────────────────────────────

    /**
     * The theme group renders `MuhabbetThemeMode.entries` and fills the row matching the controller's
     * mode, which is `fromStorageKey` of whatever is persisted. Those two facts together are what
     * make "every row unselected" impossible; this pins them.
     */
    @Test
    fun should_always_select_exactly_one_theme_row() {
        val stored = MuhabbetThemeMode.entries.map { it.storageKey } + listOf(null, "", "sepia", "OLED")
        stored.forEach { key ->
            val mode = MuhabbetThemeMode.fromStorageKey(key)
            assertEquals(
                1,
                MuhabbetThemeMode.entries.count { it == mode },
                "stored theme '$key' selected nothing or more than one row"
            )
        }
    }

    @Test
    fun should_round_trip_every_theme_storage_key() {
        MuhabbetThemeMode.entries.forEach { mode ->
            assertEquals(mode, MuhabbetThemeMode.fromStorageKey(mode.storageKey))
        }
    }

    /** A fresh install has no `app_theme` key at all, and System is the honest answer to that. */
    @Test
    fun should_select_system_theme_when_nothing_is_stored() {
        assertEquals(MuhabbetThemeMode.System, MuhabbetThemeMode.fromStorageKey(null))
        assertTrue(MuhabbetThemeMode.System in MuhabbetThemeMode.entries)
    }
}
