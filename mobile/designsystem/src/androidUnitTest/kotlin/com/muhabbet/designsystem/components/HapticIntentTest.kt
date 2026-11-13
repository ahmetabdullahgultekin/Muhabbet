package com.muhabbet.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetHaptics
import com.muhabbet.designsystem.theme.MuhabbetTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * #836: which haptic each component emits, read off a recording [HapticFeedback].
 *
 * The defect was that every non-destructive button buzzed `MessageSent` — a Cancel felt exactly
 * like a message leaving the device. So the assertions that matter most here are the empty ones:
 * a control with nothing to report must report nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HapticIntentTest {

    @get:Rule
    val rule = createComposeRule()

    private val recorded = mutableListOf<HapticFeedbackType>()
    private val recorder = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            recorded += hapticFeedbackType
        }
    }

    private fun render(content: @Composable () -> Unit) = rule.setContent {
        MuhabbetTheme {
            CompositionLocalProvider(LocalHaptics provides MuhabbetHaptics(recorder, enabled = true)) {
                content()
            }
        }
    }

    @Test
    fun `should emit nothing when a text button is tapped`() {
        render { MuhabbetButton(text = "Vazgeç", onClick = {}, role = MuhabbetButtonRole.Text) }
        rule.onNodeWithText("Vazgeç").performClick()
        assertEquals(emptyList<HapticFeedbackType>(), recorded)
    }

    @Test
    fun `should emit nothing when a secondary button is tapped`() {
        render { MuhabbetButton(text = "Atla", onClick = {}, role = MuhabbetButtonRole.Secondary) }
        rule.onNodeWithText("Atla").performClick()
        assertEquals(emptyList<HapticFeedbackType>(), recorded)
    }

    @Test
    fun `should emit nothing when a primary button names no intent`() {
        render { MuhabbetButton(text = "Kaydet", onClick = {}) }
        rule.onNodeWithText("Kaydet").performClick()
        assertEquals(emptyList<HapticFeedbackType>(), recorded)
    }

    @Test
    fun `should emit reject when a destructive button is tapped`() {
        render { MuhabbetButton(text = "Sil", onClick = {}, role = MuhabbetButtonRole.Destructive) }
        rule.onNodeWithText("Sil").performClick()
        assertEquals(listOf(HapticFeedbackType.Reject), recorded)
    }

    @Test
    fun `should emit only the configured intent when a primary button names one`() {
        render {
            MuhabbetButton(text = "Kabul et", onClick = {}, haptic = MuhabbetHapticIntent.CallAccepted)
        }
        rule.onNodeWithText("Kabul et").performClick()
        assertEquals(listOf(HapticFeedbackType.Confirm), recorded)
    }

    @Test
    fun `should still run the click when the haptic fires`() {
        var clicks = 0
        render { MuhabbetButton(text = "Sil", onClick = { clicks++ }, role = MuhabbetButtonRole.Destructive) }
        rule.onNodeWithText("Sil").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun `should emit nothing when an icon button names no intent`() {
        render { MuhabbetIconButton(icon = Muhabbet.icons.Search, contentDescription = "Ara", onClick = {}) }
        rule.onNodeWithContentDescription("Ara").performClick()
        assertEquals(emptyList<HapticFeedbackType>(), recorded)
    }

    @Test
    fun `should emit the named intent when an icon button names one`() {
        render {
            MuhabbetIconButton(
                icon = Muhabbet.icons.Close,
                contentDescription = "Reddet",
                onClick = {},
                haptic = MuhabbetHapticIntent.CallDeclined
            )
        }
        rule.onNodeWithContentDescription("Reddet").performClick()
        assertEquals(listOf(HapticFeedbackType.Reject), recorded)
    }

    @Test
    fun `should emit nothing when a fab is tapped`() {
        render { MuhabbetFab(icon = Muhabbet.icons.Add, contentDescription = "Yeni", onClick = {}) }
        rule.onNodeWithContentDescription("Yeni").performClick()
        assertEquals(emptyList<HapticFeedbackType>(), recorded)
    }

    @Test
    fun `should emit nothing when the user has turned haptics off`() {
        rule.setContent {
            MuhabbetTheme {
                CompositionLocalProvider(LocalHaptics provides MuhabbetHaptics(recorder, enabled = false)) {
                    MuhabbetButton(text = "Sil", onClick = {}, role = MuhabbetButtonRole.Destructive)
                }
            }
        }
        rule.onNodeWithText("Sil").performClick()
        assertEquals(emptyList<HapticFeedbackType>(), recorded)
    }
}
