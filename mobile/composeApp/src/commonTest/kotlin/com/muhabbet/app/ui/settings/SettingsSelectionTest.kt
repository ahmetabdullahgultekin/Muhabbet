package com.muhabbet.app.ui.settings

import com.muhabbet.designsystem.theme.MuhabbetThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun should_select_that_language_when_the_preference_and_the_screen_agree() {
        assertEquals(AppLanguage.Turkish, selectedLanguage(stored = "tr", rendered = "tr"))
        assertEquals(AppLanguage.English, selectedLanguage(stored = "en", rendered = "en"))
    }

    /**
     * #548, and the one assertion this file used to make backwards.
     *
     * The preference said Turkish, every string on screen was English, and the radio answered with
     * the preference — so it said Türkçe over English words, which is #505's headline symptom with
     * the cause moved. What is rendering is what the row must name.
     */
    @Test
    fun should_select_the_language_on_screen_when_it_disagrees_with_the_preference() {
        assertEquals(AppLanguage.English, selectedLanguage(stored = "tr", rendered = "en"))
        assertEquals(AppLanguage.Turkish, selectedLanguage(stored = "en", rendered = "tr"))
    }

    /**
     * The half that actually frees the user.
     *
     * Marking the honest row is not enough on its own: the picker skipped a tap on the row already
     * filled in, so with the preference winning, Türkçe looked chosen and tapping it did nothing.
     * Now the row that is NOT rendering is the one offered, and taking it has to restart — the
     * locale is applied in `MainActivity.onCreate` and nowhere else.
     */
    @Test
    fun should_restart_when_the_stored_language_is_picked_again_because_it_is_not_the_one_rendering() {
        assertTrue(languageNeedsRestart(AppLanguage.Turkish, rendered = "en"))
    }

    /** Restarting the app to arrive at the same screen in the same language shows nothing for it. */
    @Test
    fun should_not_restart_when_the_picked_language_is_already_the_one_rendering() {
        AppLanguage.entries.forEach { language ->
            assertFalse(
                languageNeedsRestart(language, rendered = language.code),
                "${language.code} would restart to arrive at itself"
            )
        }
    }

    /**
     * Totality of the pair: whichever row is filled in never needs a restart, and every other row
     * always does. That is what makes "one tap always either does nothing visible or fixes it".
     */
    @Test
    fun should_need_a_restart_for_exactly_the_rows_that_are_not_selected() {
        val stored = listOf(null, "tr", "en", "de", "")
        stored.forEach { s ->
            AppLanguage.entries.map { it.code }.forEach { r ->
                val selected = selectedLanguage(stored = s, rendered = r)
                AppLanguage.entries.forEach { language ->
                    assertEquals(
                        language != selected,
                        languageNeedsRestart(language, rendered = r),
                        "stored=$s rendered=$r language=${language.code}"
                    )
                }
            }
        }
    }

    /** User data, so a value the app no longer offers must not leave the group with nothing filled. */
    @Test
    fun should_ignore_a_stored_language_the_app_does_not_offer() {
        assertEquals(AppLanguage.English, selectedLanguage(stored = "de", rendered = "en"))
        assertEquals(AppLanguage.Turkish, selectedLanguage(stored = "", rendered = "tr"))
    }

    /**
     * Unreachable while `app_language_code` is declared in both locales, but the function is total
     * and this is the only input for which the preference is the best answer available.
     */
    @Test
    fun should_fall_back_to_the_preference_when_the_rendered_code_names_no_offered_language() {
        assertEquals(AppLanguage.English, selectedLanguage(stored = "en", rendered = "de"))
        assertEquals(AppLanguage.Turkish, selectedLanguage(stored = null, rendered = "de"))
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
