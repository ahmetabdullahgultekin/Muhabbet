package com.muhabbet.designsystem.components

import androidx.compose.ui.unit.Density
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The pure decisions inside components, testable without composing anything: the nav label's font
 * cap (#770), the shimmer band's geometry (#835) and the settings rows' haptics (#837).
 */
class ComponentPolicyTest {

    // ── Nav labels (#770) ─────────────────────────────────────────────

    @Test
    fun should_leave_a_nav_labels_density_alone_below_the_cap() {
        val user = Density(2.75f, fontScale = 1.2f)
        assertSame(user, cappedFontScaleDensity(user, NavLabelMaxFontScale))
    }

    @Test
    fun should_cap_a_nav_labels_font_scale_above_the_cap() {
        listOf(1.5f, 2f).forEach { scale ->
            val capped = cappedFontScaleDensity(Density(2.75f, fontScale = scale), NavLabelMaxFontScale)
            assertEquals(NavLabelMaxFontScale, capped.fontScale)
            assertEquals(2.75f, capped.density, "only the font scale is capped, never the density")
        }
    }

    @Test
    fun should_floor_a_shrunk_nav_label_a_little_below_its_design_size() {
        // Never below 9sp for a 12sp label: the floor iOS tab bars use.
        assertTrue(NavLabelMinFontScale < 1f && 12f * NavLabelMinFontScale >= 9f)
    }

    // ── Shimmer geometry (#835) ───────────────────────────────────────

    @Test
    fun should_start_the_shimmer_band_entirely_before_the_element_and_end_entirely_after_it() {
        listOf(48f, 360f, 1440f).forEach { width ->
            val band = width * 0.6f
            assertEquals(-band, shimmerBandLeft(0f, width, band))
            assertEquals(width, shimmerBandLeft(1f, width, band))
        }
    }

    @Test
    fun should_sweep_the_same_fraction_of_the_element_at_every_density() {
        // The same element at mdpi (100 px) and xxxhdpi (400 px): at every phase the band sits at
        // the same fraction of the width. With the old fixed 2000 px travel it did not.
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { phase ->
            val mdpi = shimmerBandLeft(phase, 100f, 60f) / 100f
            val xxxhdpi = shimmerBandLeft(phase, 400f, 240f) / 400f
            assertEquals(mdpi, xxxhdpi, 0.0001f, "phase $phase")
        }
    }

    // ── Settings row haptics (#837) ───────────────────────────────────

    @Test
    fun should_buzz_on_or_off_for_the_state_a_toggle_row_is_moving_to() {
        assertEquals(MuhabbetHapticIntent.ToggleOn, RowInteraction.Toggle(checked = false, enabled = true) {}.hapticFor())
        assertEquals(MuhabbetHapticIntent.ToggleOff, RowInteraction.Toggle(checked = true, enabled = true) {}.hapticFor())
    }

    @Test
    fun should_tick_when_a_radio_row_changes_the_selection() {
        assertEquals(MuhabbetHapticIntent.SegmentAdvanced, RowInteraction.Select(selected = false) {}.hapticFor())
    }

    @Test
    fun should_stay_silent_when_the_already_selected_radio_row_is_tapped_again() {
        assertNull(RowInteraction.Select(selected = true) {}.hapticFor())
    }

    @Test
    fun should_not_buzz_for_navigation_or_inert_rows() {
        assertNull(RowInteraction.Navigate {}.hapticFor())
        assertNull(RowInteraction.None.hapticFor())
    }
}
