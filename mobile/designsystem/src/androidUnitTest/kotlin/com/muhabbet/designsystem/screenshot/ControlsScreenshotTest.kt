package com.muhabbet.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.test.junit4.createComposeRule
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetChip
import com.muhabbet.designsystem.components.MuhabbetSwitch
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
            // #771: an icon and a label long enough to wrap are centred as a group, line by line,
            // and stop at two lines.
            MuhabbetButton(
                text = "Gruba katılmak için davet bağlantısını paylaş ve onayla",
                onClick = {},
                leadingIcon = Muhabbet.icons.Group,
                role = MuhabbetButtonRole.Secondary
            )
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
}
