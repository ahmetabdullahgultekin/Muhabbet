package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.MuhabbetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The two interactions that run every frame must not recompose anything: a press (#846) and a
 * scroll under the top bar (#845).
 *
 * Measured as the Recomposer's applied-change count across the whole interaction, which is stricter
 * than a counter in one composable — it also catches a recomposition that a slot lambda would have
 * been skipped through. Both the press squeeze and the bar's lift are read in the draw phase, so the
 * count must not move at all.
 *
 * The button under test is a Text button on purpose: Material's filled `Button` animates its own
 * shadow elevation through composition state on press, which is Material's behaviour and not the
 * press feedback this module owns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecompositionBudgetTest {

    @get:Rule
    val rule = createComposeRule()

    private fun appliedChanges(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

    private fun render(content: @Composable () -> Unit) = rule.setContent { MuhabbetTheme(hapticsEnabled = false) { content() } }

    private fun assertNoRecompositionDuring(action: () -> Unit) {
        rule.waitForIdle()
        val before = appliedChanges()
        action()
        rule.waitForIdle()
        assertEquals("recompositions during the interaction", before, appliedChanges())
    }

    @Test
    fun `should not recompose a button when it is pressed and released`() {
        render { MuhabbetButton(text = "Vazgeç", onClick = {}, role = MuhabbetButtonRole.Text) }
        val button = rule.onNodeWithText("Vazgeç")
        assertNoRecompositionDuring {
            button.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(PRESS_HOLD_MS)
            button.performTouchInput { up() }
        }
    }

    @Test
    fun `should not recompose an icon button when it is pressed and released`() {
        render { MuhabbetIconButton(icon = Muhabbet.icons.Search, contentDescription = "Ara", onClick = {}) }
        val button = rule.onNodeWithContentDescription("Ara")
        assertNoRecompositionDuring {
            button.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(PRESS_HOLD_MS)
            button.performTouchInput { up() }
        }
    }

    @Test
    fun `should not recompose a settings row when it is pressed and released`() {
        render { SettingsNavRow(title = "Gizlilik", onClick = {}) }
        val row = rule.onNodeWithText("Gizlilik")
        assertNoRecompositionDuring {
            row.performTouchInput { down(center) }
            rule.mainClock.advanceTimeBy(PRESS_HOLD_MS)
            row.performTouchInput { up() }
        }
    }

    @Test
    fun `should lift the bar through the scaffold and drop it again at the top`() {
        lateinit var scroll: MuhabbetTopBarScroll
        render {
            scroll = rememberMuhabbetTopBarScroll()
            ScrollingScreen(scroll)
        }
        assertEquals(0f, scroll.contentOffset)

        rule.onNodeWithTag(CONTENT_TAG).performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTrue("content scrolled under the bar must lift it", scroll.liftFraction(LIFT_PX) == 1f)

        rule.onNodeWithTag(CONTENT_TAG).performTouchInput { swipeDown() }
        rule.onNodeWithTag(CONTENT_TAG).performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals("back at the top, the bar is at rest", 0f, scroll.liftFraction(LIFT_PX))
    }

    @Test
    fun `should not recompose the top bar while content scrolls under it`() {
        render { ScrollingScreen(rememberMuhabbetTopBarScroll()) }
        assertNoRecompositionDuring {
            rule.onNodeWithTag(CONTENT_TAG).performTouchInput { swipeUp() }
        }
    }

    @Composable
    private fun ScrollingScreen(scroll: MuhabbetTopBarScroll) {
        MuhabbetScaffold(
            topBar = { MuhabbetTopBar(title = "Sohbetler") },
            topBarScroll = scroll
        ) { padding ->
            Column(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .testTag(CONTENT_TAG)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("İçerik")
                Spacer(Modifier.height(Muhabbet.sizes.BubbleMaxWidth * TALL_CONTENT_MULTIPLIER))
            }
        }
    }

    private companion object {
        const val CONTENT_TAG = "content"
        const val PRESS_HOLD_MS = 500L
        const val LIFT_PX = 1f
        const val TALL_CONTENT_MULTIPLIER = 10
    }
}
