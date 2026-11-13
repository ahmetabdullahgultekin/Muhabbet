package com.muhabbet.designsystem.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.captureRoboImage
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetChip
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.MuhabbetSwitch
import com.muhabbet.designsystem.components.SettingsNavRow
import com.muhabbet.designsystem.theme.MuhabbetTheme
import com.muhabbet.designsystem.theme.MuhabbetThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens for the control family. Labels are fixtures, not product copy — the design system carries
 * no strings — and deliberately include İ ı Ğ ş: a golden that renders them in the fallback font is
 * the regression this harness exists to catch (design doc §4, "Latin Extended-A is not optional").
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1280dp-xhdpi")
class ControlsScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun buttons() = MuhabbetSnapshotMatrix.capture(rule, "button") {
        Column(verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Medium)) {
            MuhabbetButtonRole.entries.forEach { role ->
                MuhabbetButton(text = "İleri · Ğüşıöç · $role", onClick = {}, role = role)
            }
            MuhabbetButton(text = "Devre dışı", onClick = {}, enabled = false)
        }
    }

    @Test
    fun switches() = MuhabbetSnapshotMatrix.capture(rule, "switch") {
        Row(horizontalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Large)) {
            MuhabbetSwitch(checked = true, onCheckedChange = {})
            MuhabbetSwitch(checked = false, onCheckedChange = {})
            MuhabbetSwitch(checked = true, onCheckedChange = {}, enabled = false)
        }
    }

    @Test
    fun chips() = MuhabbetSnapshotMatrix.capture(rule, "chip") {
        Row(horizontalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Small)) {
            MuhabbetChip(label = "Tümü", selected = true, onClick = {})
            MuhabbetChip(label = "Okunmamış", selected = false, onClick = {})
            MuhabbetChip(label = "Gruplar", selected = false, onClick = {}, enabled = false)
        }
    }

    /**
     * #846: the press squeeze, held. One button, one icon button and one row, each held down with
     * its own pointer, so the three strengths ([com.muhabbet.designsystem.modifier.MuhabbetPress])
     * are visible side by side. One variant only: the squeeze is geometry, not colour.
     *
     * The ripple is switched off (`LocalRippleConfiguration provides null`) for the capture. On
     * Android it is animated by the platform's RippleDrawable on its own clock, which the Compose
     * test clock does not drive, so its frame would differ run to run; what this golden pins is the
     * design system's own feedback.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun pressed() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MuhabbetTheme(mode = MuhabbetThemeMode.Light, hapticsEnabled = false) {
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    Column(
                        Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(Muhabbet.spacing.Large),
                        verticalArrangement = Arrangement.spacedBy(Muhabbet.spacing.Medium)
                    ) {
                        MuhabbetButton(text = "Basılı düğme", onClick = {})
                        MuhabbetIconButton(icon = Muhabbet.icons.Search, contentDescription = "Ara", onClick = {})
                        SettingsNavRow(title = "Gizlilik", subtitle = "Son görülme, okundu bilgisi", onClick = {})
                    }
                }
            }
        }
        rule.onNodeWithText("Basılı düğme").performTouchInput { down(0, center) }
        rule.onNodeWithContentDescription("Ara").performTouchInput { down(1, center) }
        rule.onNodeWithText("Gizlilik").performTouchInput { down(2, center) }
        rule.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
        rule.onRoot().captureRoboImage("src/androidUnitTest/goldens/pressed/pressed_light_x10.png")
    }

    private companion object {
        const val PRESS_SETTLE_MS = 1_000L
    }
}
