package com.muhabbet.designsystem.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasText
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetHaptics
import com.muhabbet.designsystem.theme.MuhabbetTheme
import com.muhabbet.designsystem.theme.breathing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Behaviour that a screenshot cannot show: accessibility semantics, haptics, recomposition and
 * density. Each test here fails against the implementation its issue describes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1280dp-xhdpi")
class ComponentBehaviourTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private class RecordingHaptics : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    private fun setThemed(haptics: HapticFeedback? = null, reducedMotion: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            MuhabbetTheme(hapticsEnabled = false, reducedMotion = reducedMotion) {
                if (haptics != null) {
                    CompositionLocalProvider(LocalHaptics provides MuhabbetHaptics(haptics, enabled = true), content = content)
                } else {
                    content()
                }
            }
        }
    }

    private fun hasRole(role: Role) = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    // ── #837: settings rows own one interaction ───────────────────────

    @Test
    fun should_expose_a_switch_row_as_exactly_one_toggleable_switch_node() {
        setThemed { SettingsSwitchRow(title = "Okundu bilgisi", checked = true, onCheckedChange = {}) }

        val clickable = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertEquals("one focusable control, not a row plus a switch", 1, clickable.size)
        rule.onNode(hasRole(Role.Switch) and hasText("Okundu bilgisi")).assertIsOn()
    }

    @Test
    fun should_toggle_and_buzz_once_when_the_row_itself_is_tapped() {
        val haptics = RecordingHaptics()
        var checked by mutableStateOf(true)
        setThemed(haptics) { SettingsSwitchRow(title = "Okundu bilgisi", checked = checked, onCheckedChange = { checked = it }) }

        rule.onNode(hasRole(Role.Switch)).performClick()

        assertFalse(checked)
        rule.onNode(hasRole(Role.Switch)).assertIsOff()
        assertEquals(listOf(HapticFeedbackType.ToggleOff), haptics.performed)
    }

    @Test
    fun should_expose_radio_rows_as_radio_buttons_inside_one_selectable_group() {
        setThemed {
            SettingsRadioGroup {
                SettingsRadioRow(title = "Herkes", selected = true, onSelect = {})
                SettingsRadioRow(title = "Kişilerim", selected = false, onSelect = {})
            }
        }

        assertEquals(2, rule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        val inGroup = hasParent(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        assertEquals(2, rule.onAllNodes(hasRole(Role.RadioButton) and inGroup).fetchSemanticsNodes().size)
    }

    @Test
    fun should_tick_only_when_a_radio_row_changes_the_selection() {
        val haptics = RecordingHaptics()
        var selected by mutableStateOf("Herkes")
        setThemed(haptics) {
            SettingsRadioGroup {
                listOf("Herkes", "Kişilerim").forEach { option ->
                    SettingsRadioRow(title = option, selected = selected == option, onSelect = { selected = option })
                }
            }
        }

        rule.onNode(hasRole(Role.RadioButton) and hasText("Herkes")).performClick()
        assertEquals("re-selecting the current option is not feedback", emptyList<HapticFeedbackType>(), haptics.performed)

        rule.onNode(hasRole(Role.RadioButton) and hasText("Kişilerim")).performClick()
        assertEquals(listOf(HapticFeedbackType.SegmentTick), haptics.performed)
        assertEquals("Kişilerim", selected)
    }

    @Test
    fun should_announce_a_nav_row_as_a_button_and_a_busy_one_by_its_state() {
        setThemed {
            Column {
                SettingsNavRow(title = "Engellenenler", onClick = {})
                SettingsNavRow(title = "Dışa aktar", onClick = {}, loading = true, loadingDescription = "Hazırlanıyor")
            }
        }
        rule.onNode(hasRole(Role.Button) and hasText("Engellenenler")).assertExists()
        rule.onNode(
            hasText("Dışa aktar") and SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Hazırlanıyor")
        ).assertExists()
    }

    // ── #770: the nav bar names every tab in full ─────────────────────

    @Test
    fun should_name_each_tab_in_full_to_a_screen_reader_at_large_font_scale() {
        val labels = listOf("Sohbetler", "Güncellemeler", "Topluluklar", "Aramalar")
        setThemed {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                MuhabbetNavBar(
                    items = labels.map { MuhabbetNavItem(icon = Muhabbet.icons.Person, label = it) },
                    selectedIndex = 0,
                    onSelect = {}
                )
            }
        }
        labels.forEach { label ->
            // Displayed, not merely present: each tab must get its own quarter of the bar.
            rule.onNode(hasContentDescription(label) and hasRole(Role.Tab)).assertIsDisplayed()
        }
        val widths = rule.onAllNodes(hasRole(Role.Tab)).fetchSemanticsNodes().map { it.boundsInRoot.width }
        assertEquals(4, widths.size)
        assertTrue("tabs must share the bar equally: $widths", widths.max() - widths.min() < 2f)
    }

    // ── #835: the shimmer animates in draw, not in composition ────────

    @Test
    fun should_not_recompose_the_skeleton_tree_while_the_shimmer_runs() {
        rule.mainClock.autoAdvance = false
        var compositions = 0
        setThemed {
            ShimmerHost {
                SideEffect { compositions++ }
                MuhabbetSkeletonList(rows = 3)
            }
        }
        rule.mainClock.advanceTimeBy(FrameMs * 4)
        val settled = compositions

        rule.mainClock.advanceTimeBy(FrameMs * 60)

        assertEquals("a running shimmer recomposed its host's content", settled, compositions)
    }

    @Test
    fun should_draw_a_different_frame_as_the_shimmer_sweeps() {
        rule.mainClock.autoAdvance = false
        setThemed { ShimmerBlockUnderTest() }
        val early = captureTagged(ShimmerTag, afterMs = 200)
        val later = captureTagged(ShimmerTag, afterMs = 500)
        assertFalse("the sweep must actually move", early.sameAs(later))
    }

    @Test
    fun should_hold_the_shimmer_still_under_reduced_motion() {
        rule.mainClock.autoAdvance = false
        setThemed(reducedMotion = true) { ShimmerBlockUnderTest() }
        val early = captureTagged(ShimmerTag, afterMs = 200)
        val later = captureTagged(ShimmerTag, afterMs = 500)
        assertTrue("reduced motion must not sweep", early.sameAs(later))
    }

    @Composable
    private fun ShimmerBlockUnderTest() {
        MuhabbetSkeletonBlock(Modifier.width(Muhabbet.sizes.BubbleMaxWidth).testTag(ShimmerTag))
    }

    private fun captureTagged(tag: String, afterMs: Long): Bitmap {
        rule.mainClock.advanceTimeBy(afterMs)
        return captureNode(tag)
    }

    /**
     * Draws the Compose view into a bitmap and crops it to [tag]'s bounds. `captureToImage()` waits
     * for a platform frame callback that never comes while the test clock is paused, which is the
     * only way to look at a looping animation at a chosen instant; drawing the view directly is
     * what Roborazzi does too.
     */
    private fun captureNode(tag: String): Bitmap {
        val bounds = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val content = rule.activity.findViewById<ViewGroup>(android.R.id.content)
        val view = content.getChildAt(0)
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(full))
        return Bitmap.createBitmap(
            full,
            bounds.left.roundToInt(),
            bounds.top.roundToInt(),
            bounds.width.roundToInt(),
            bounds.height.roundToInt()
        )
    }

    // ── #842: loops stop under reduced motion ─────────────────────────

    @Test
    fun should_return_the_modifier_unchanged_from_breathing_under_reduced_motion() {
        var reduced: Modifier? = null
        var full: Modifier? = null
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MuhabbetTheme(hapticsEnabled = false, reducedMotion = true) { reduced = Modifier.breathing() }
            MuhabbetTheme(hapticsEnabled = false, reducedMotion = false) { full = Modifier.breathing() }
        }
        rule.mainClock.advanceTimeBy(FrameMs)
        assertSame(Modifier, reduced)
        assertNotSame(Modifier, full)
    }

    // ── #841: the empty-chats art is the same picture at every density ─

    @Test
    fun should_draw_the_empty_chats_art_identically_at_mdpi_and_xxxhdpi() {
        var density by mutableStateOf(Density(1f))
        setThemed(reducedMotion = true) {
            CompositionLocalProvider(LocalDensity provides density) {
                EmptyChatsIllustration(title = "", subtitle = "", modifier = Modifier.testTag(ArtTag))
            }
        }
        val mdpi = captureNode(ArtTag)
        density = Density(4f)
        rule.waitForIdle()
        val xxxhdpi = captureNode(ArtTag)

        assertEquals(mdpi.width * 4, xxxhdpi.width)
        val scaledDown = Bitmap.createScaledBitmap(xxxhdpi, mdpi.width, mdpi.height, true)
        val difference = meanChannelDifference(mdpi, scaledDown)
        assertTrue("art differs by $difference per channel between densities", difference < MaxMeanChannelDifference)
    }

    private fun meanChannelDifference(a: Bitmap, b: Bitmap): Double {
        var total = 0L
        for (y in 0 until a.height) for (x in 0 until a.width) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            total += abs((p shr 16 and 0xFF) - (q shr 16 and 0xFF)) +
                abs((p shr 8 and 0xFF) - (q shr 8 and 0xFF)) +
                abs((p and 0xFF) - (q and 0xFF))
        }
        return total.toDouble() / (a.width * a.height * 3)
    }

    private companion object {
        const val FrameMs = 16L
        const val ShimmerTag = "shimmer"
        const val ArtTag = "art"

        /**
         * Antialiasing differs between a 120 px and a 480 px rendering, so pixel equality is not the
         * bar. The px-sized art this replaced drew its bubbles at a quarter of the size at 4x, which
         * is a mean difference an order of magnitude above this.
         */
        const val MaxMeanChannelDifference = 4.0
    }
}
