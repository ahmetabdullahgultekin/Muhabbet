package com.muhabbet.designsystem.components

import androidx.compose.material3.ExperimentalMaterial3Api
import com.muhabbet.designsystem.theme.MuhabbetComponentSizes
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The pure rules behind the second-wave components (#847): badge capping, the search policy, the
 * pull-to-refresh arming machine, the tab indicator's interpolation and the date picker's minimum.
 * Each was a copy-pasted fragment in some screen before; here each has one definition and a test.
 */
class CataloguePolicyTest {

    // ── formatBadgeCount ────────────────────────────────────────

    @Test
    fun should_return_null_when_count_is_zero_or_negative() {
        assertNull(formatBadgeCount(0))
        assertNull(formatBadgeCount(-3))
    }

    @Test
    fun should_show_exact_count_when_at_or_below_max() {
        assertEquals("1", formatBadgeCount(1))
        assertEquals("99", formatBadgeCount(99))
    }

    @Test
    fun should_cap_with_plus_when_count_exceeds_max() {
        assertEquals("99+", formatBadgeCount(100))
        assertEquals("99+", formatBadgeCount(1342))
        assertEquals("9+", formatBadgeCount(10, max = 9))
    }

    @Test
    fun should_treat_a_cap_below_one_as_one_when_max_is_degenerate() {
        assertEquals("1+", formatBadgeCount(5, max = 0))
    }

    // ── effectiveSearchQuery ────────────────────────────────────

    @Test
    fun should_trim_when_query_has_surrounding_whitespace() {
        assertEquals("ayşe", effectiveSearchQuery("  ayşe ", minLength = 0))
    }

    @Test
    fun should_return_empty_when_trimmed_query_is_below_minimum() {
        assertEquals("", effectiveSearchQuery("a ", minLength = 2))
        assertEquals("", effectiveSearchQuery("   ", minLength = 0))
    }

    @Test
    fun should_keep_query_when_trimmed_length_reaches_minimum() {
        assertEquals("ay", effectiveSearchQuery(" ay ", minLength = 2))
    }

    // ── PullRefreshArming ───────────────────────────────────────

    @Test
    fun should_fire_once_when_a_pull_crosses_the_threshold() {
        var state: PullRefreshArming = PullRefreshArming.Idle
        val fired = listOf(0.2f, 0.8f, 1.0f, 1.3f, 1.1f).map { fraction ->
            val step = state.next(fraction, refreshing = false)
            state = step.state
            step.fire
        }
        assertEquals(listOf(false, false, true, false, false), fired)
        assertEquals(PullRefreshArming.Armed, state)
    }

    @Test
    fun should_rearm_when_the_pull_falls_back_below_the_threshold() {
        val armed = PullRefreshArming.Idle.next(1f, refreshing = false)
        val dropped = armed.state.next(0.6f, refreshing = false)
        val again = dropped.state.next(1.05f, refreshing = false)
        assertEquals(PullRefreshArming.Idle, dropped.state)
        assertFalse(dropped.fire)
        assertTrue(again.fire)
    }

    @Test
    fun should_stay_silent_when_the_indicator_reaches_threshold_during_a_refresh() {
        // A programmatic isRefreshing = true animates the indicator to the threshold with no finger.
        val step = PullRefreshArming.Idle.next(1f, refreshing = true)
        assertFalse(step.fire)
        assertEquals(PullRefreshArming.Armed, step.state)
    }

    // ── indicatorBounds ─────────────────────────────────────────

    private val spans = listOf(
        TabSpan(left = MuhabbetSpacing.None, width = MuhabbetSizes.PickerSheetHeight / 4, contentWidth = MuhabbetSizes.AvatarLarge),
        TabSpan(left = MuhabbetSizes.PickerSheetHeight / 4, width = MuhabbetSizes.PickerSheetHeight / 4, contentWidth = MuhabbetSizes.AvatarHero)
    )

    @Test
    fun should_center_under_the_label_when_position_is_a_whole_tab() {
        val (left, width) = indicatorBounds(spans, 0f)
        assertEquals(MuhabbetSizes.AvatarLarge, width)
        assertEquals((spans[0].width - MuhabbetSizes.AvatarLarge) / 2, left)
    }

    @Test
    fun should_interpolate_when_position_is_between_tabs() {
        val (left, width) = indicatorBounds(spans, 0.5f)
        val fromLeft = (spans[0].width - spans[0].contentWidth) / 2
        val toLeft = spans[1].left + (spans[1].width - spans[1].contentWidth) / 2
        assertEquals((spans[0].contentWidth + spans[1].contentWidth) / 2, width)
        assertEquals((fromLeft + toLeft) / 2, left)
    }

    @Test
    fun should_clamp_when_position_runs_past_the_last_tab() {
        assertEquals(indicatorBounds(spans, 1f), indicatorBounds(spans, 3.7f))
        assertEquals(indicatorBounds(spans, 0f), indicatorBounds(spans, -1f))
    }

    // ── List density ────────────────────────────────────────────

    @Test
    fun should_never_drop_below_the_touch_target_when_density_is_compact() {
        MuhabbetListDensity.entries.forEach { density ->
            listOf(true, false).forEach { twoLine ->
                assertTrue(minHeightFor(density, twoLine) >= MuhabbetSizes.MinTouchTarget, "$density twoLine=$twoLine")
            }
        }
        assertEquals(MuhabbetSizes.ChatListItemMinHeight, MuhabbetComponentSizes.ListItemTwoLineComfortable)
    }

    // ── Date picker minimum ─────────────────────────────────────

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun should_allow_the_minimum_day_itself_when_the_minimum_is_mid_day() {
        val day = 1_741_910_400_000L // 2025-03-14 00:00 UTC
        val afternoon = day + 15 * 60 * 60 * 1000L
        val dates = selectableFrom(afternoon)
        assertTrue(dates.isSelectableDate(day), "the minimum day is pickable all day")
        assertFalse(dates.isSelectableDate(day - 24 * 60 * 60 * 1000L), "the day before is not")
        assertTrue(dates.isSelectableYear(2025))
        assertFalse(dates.isSelectableYear(2024))
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun should_allow_everything_when_there_is_no_minimum() {
        val dates = selectableFrom(null)
        assertTrue(dates.isSelectableDate(0L))
        assertTrue(dates.isSelectableYear(1970))
    }

    @Test
    fun should_compute_the_gregorian_year_when_given_an_epoch_day() {
        assertEquals(1970, yearOfUtcDay(0))
        assertEquals(1969, yearOfUtcDay(-1))
        assertEquals(2000, yearOfUtcDay(10_957)) // 2000-01-01
        assertEquals(2024, yearOfUtcDay(20_088)) // 2024-12-31, a leap year's last day
        assertEquals(2025, yearOfUtcDay(20_161)) // 2025-03-14
    }
}
