package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetTheme
import com.muhabbet.designsystem.theme.MuhabbetThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Semantics, touch targets and haptics for the second-wave components (#847) — the half of a
 * component a golden cannot see. Each test names the behaviour it pins and fails if the component
 * stops providing it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CatalogueSemanticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val haptics = mutableListOf<HapticFeedbackType>()

    private fun setContent(content: @Composable () -> Unit) {
        val recorder = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                haptics += hapticFeedbackType
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides recorder) {
                MuhabbetTheme(mode = MuhabbetThemeMode.Light, hapticsEnabled = true, content = content)
            }
        }
    }

    // ── Count badge ─────────────────────────────────────────────

    @Test
    fun `should cap the count and announce it when the badge exceeds max`() {
        setContent { MuhabbetCountBadge(count = 1342) }
        rule.onNodeWithContentDescription("99+").assertIsDisplayed()
    }

    @Test
    fun `should prefer the caller's sentence when a badge has a content description`() {
        setContent { MuhabbetCountBadge(count = 5, contentDescription = "5 okunmamış mesaj") }
        rule.onNodeWithContentDescription("5 okunmamış mesaj").assertIsDisplayed()
    }

    @Test
    fun `should draw nothing when the badge count is zero`() {
        setContent { MuhabbetCountBadge(count = 0, contentDescription = "boş") }
        rule.onNodeWithContentDescription("boş").assertDoesNotExist()
    }

    // ── List item ───────────────────────────────────────────────

    @Test
    fun `should be a button at least the touch target tall when a list item is clickable`() {
        var clicks = 0
        setContent { MuhabbetListItem(headline = "Bildirimler", onClick = { clicks++ }) }
        rule.onNodeWithText("Bildirimler", useUnmergedTree = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHeightIsAtLeast(MuhabbetSizes.MinTouchTarget)
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `should expose a long-click action and buzz when a list item has one`() {
        var longClicks = 0
        setContent { MuhabbetListItem(headline = "Sohbet", onClick = {}, onLongClick = { longClicks++ }) }
        val node = rule.onNodeWithText("Sohbet")
        node.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
        rule.runOnIdle { node.fetchSemanticsNode().config[SemanticsActions.OnLongClick].action?.invoke() }
        rule.runOnIdle {
            assertEquals(1, longClicks)
            assertEquals(listOf(HapticFeedbackType.LongPress), haptics)
        }
    }

    @Test
    fun `should report selection only when the row is part of a selection`() {
        setContent {
            Column {
                MuhabbetPersonRow(name = "Seçili", avatarUrl = null, selection = MuhabbetListSelection.Selected, onClick = {})
                MuhabbetPersonRow(name = "Seçilmemiş", avatarUrl = null, selection = MuhabbetListSelection.Unselected, onClick = {})
                MuhabbetPersonRow(name = "Seçimsiz", avatarUrl = null, onClick = {})
            }
        }
        rule.onNodeWithText("Seçili").assertIsSelected()
        rule.onNodeWithText("Seçilmemiş").assertIsNotSelected()
        rule.onNodeWithText("Seçimsiz").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun `should meet the touch target when a list item is compact`() {
        setContent { MuhabbetListItem(headline = "Sıkı", density = MuhabbetListDensity.Compact, onClick = {}) }
        rule.onNodeWithText("Sıkı").assertHeightIsAtLeast(MuhabbetSizes.MinTouchTarget)
    }

    // ── Checkbox / radio ────────────────────────────────────────

    @Test
    fun `should toggle with distinct haptics and a 48dp target when the checkbox is tapped`() {
        var checked by mutableStateOf(false)
        setContent { MuhabbetCheckbox(checked = checked, onCheckedChange = { checked = it }) }
        val box = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        box.assertIsOff()
            .assertWidthIsAtLeast(MuhabbetSizes.MinTouchTarget)
            .assertHeightIsAtLeast(MuhabbetSizes.MinTouchTarget)
            .performClick()
        box.assertIsOn().performClick()
        box.assertIsOff()
        assertEquals(listOf(HapticFeedbackType.ToggleOn, HapticFeedbackType.ToggleOff), haptics)
    }

    @Test
    fun `should tick once when a radio becomes selected and stay silent when it already is`() {
        var selected by mutableStateOf(false)
        setContent { MuhabbetRadio(selected = selected, onClick = { selected = true }) }
        val radio = rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        radio.assertIsNotSelected()
            .assertWidthIsAtLeast(MuhabbetSizes.MinTouchTarget)
            .performClick()
        radio.assertIsSelected().performClick()
        assertEquals(listOf(HapticFeedbackType.SegmentTick), haptics)
    }

    // ── Card ────────────────────────────────────────────────────

    @Test
    fun `should be a button only when the card has an action`() {
        var clicks = 0
        setContent {
            Column {
                MuhabbetCard(onClick = { clicks++ }) { androidx.compose.material3.Text("Eylemli") }
                MuhabbetCard { androidx.compose.material3.Text("Eylemsiz") }
            }
        }
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        assertEquals(1, clicks)
        rule.onNodeWithText("Eylemsiz").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    // ── Spinner ─────────────────────────────────────────────────

    @Test
    fun `should report determinate progress and its label when a spinner has a value`() {
        setContent { MuhabbetSpinner(progress = 0.4f, contentDescription = "Yükleniyor") }
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithContentDescription("Yükleniyor")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.4f, 0f..1f)))
    }

    // ── Search field ────────────────────────────────────────────

    @Test
    fun `should debounce, trim and apply the minimum length when searching`() {
        val searches = mutableListOf<String>()
        var query by mutableStateOf("")
        setContent {
            MuhabbetSearchField(
                query = query,
                onQueryChange = { query = it },
                onSearch = { searches += it },
                placeholder = "Ara",
                clearContentDescription = "Temizle",
                minQueryLength = 2
            )
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Ara").performTextInput("a")
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals("under the minimum length nothing is searched", emptyList<String>(), searches)

        rule.onNodeWithText("a").performTextInput("y ")
        rule.mainClock.advanceTimeBy(DefaultSearchDebounce.inWholeMilliseconds / 2)
        assertEquals("still inside the debounce window", emptyList<String>(), searches)
        rule.mainClock.advanceTimeBy(DefaultSearchDebounce.inWholeMilliseconds)
        assertEquals(listOf("ay"), searches)

        rule.onNodeWithContentDescription("Temizle").performClick()
        rule.mainClock.advanceTimeByFrame()
        assertEquals("clearing fires immediately with no query", listOf("ay", ""), searches)
    }

    @Test
    fun `should search immediately when the keyboard search key is pressed`() {
        val searches = mutableListOf<String>()
        var query by mutableStateOf("")
        setContent {
            MuhabbetSearchField(query = query, onQueryChange = { query = it }, onSearch = { searches += it }, placeholder = "Ara", clearContentDescription = "Temizle")
        }
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Ara").performTextInput("Işıl")
        rule.mainClock.advanceTimeByFrame()
        rule.onNodeWithText("Işıl").performImeAction()
        assertEquals(listOf("Işıl"), searches)
        // The debounce that started on the keystroke must not send the same query a second time.
        rule.mainClock.advanceTimeBy(DefaultSearchDebounce.inWholeMilliseconds * 2)
        assertEquals(listOf("Işıl"), searches)
    }

    @Test
    fun `should not search on first composition when a query is restored`() {
        val searches = mutableListOf<String>()
        setContent {
            MuhabbetSearchField(query = "kayıtlı", onQueryChange = {}, onSearch = { searches += it }, placeholder = "Ara", clearContentDescription = "Temizle")
        }
        rule.mainClock.advanceTimeBy(2_000)
        assertEquals(emptyList<String>(), searches)
    }

    // ── Tabs ────────────────────────────────────────────────────

    @Test
    fun `should mark the selected tab and tick only on a real change`() {
        var selected by mutableStateOf(0)
        setContent { MuhabbetTabs(tabs = listOf("Bir", "İki"), selectedIndex = selected, onSelect = { selected = it }) }
        rule.onNodeWithText("Bir").assertIsSelected().performClick()
        rule.onNodeWithText("İki").assertIsNotSelected().performClick()
        rule.onNodeWithText("İki").assertIsSelected()
        assertEquals(listOf(HapticFeedbackType.SegmentTick), haptics)
    }

    // ── Image ───────────────────────────────────────────────────

    @Test
    fun `should announce an image with its description and image role even when it failed`() {
        setContent { MuhabbetImage(model = null, contentDescription = "Profil fotoğrafı") }
        rule.onNodeWithContentDescription("Profil fotoğrafı")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Image))
    }

    // ── Pill ────────────────────────────────────────────────────

    @Test
    fun `should not be clickable when it is a pill`() {
        setContent { MuhabbetPill(text = "Yönetici") }
        rule.onNodeWithText("Yönetici").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }
}
