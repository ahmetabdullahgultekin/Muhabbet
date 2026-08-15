package com.muhabbet.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing

/**
 * The one settings-row geometry, in the four shapes settings actually needs.
 *
 * There were two incompatible geometries for the same thing. `SettingsSections` used a `Surface`
 * with `Arrangement.spacedBy`, a 22dp icon and `vertical = 14.dp`; `PrivacyDashboardScreen`
 * reimplemented all of it with a bare `Row`, a `Spacer`, `IconLarge` and `vertical = Medium` —
 * including a switch row that is a *verbatim* copy down to the two string resources it reads. The
 * dashboard could have imported the first one and did not, so the two drifted.
 *
 * One geometry now: the dashboard's, which supports a subtitle — the reason it was written in the
 * first place — with the minimum tap target the other one had.
 */
@Composable
private fun RowFrame(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MuhabbetSizes.MinTouchTarget)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium),
        content = content
    )
}

@Composable
private fun RowLabels(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A row that does something when tapped — usually opening a sub-screen.
 *
 * @param onClick required, and deliberately not nullable. The dashboard had a row wired to
 *   `.clickable { }` — an empty body — so "Blocked contacts" looked tappable and did nothing.
 *   A required callback makes that state unexpressible; a row that goes nowhere is a
 *   [SettingsInfoRow], not a disguised button.
 * @param loading swaps the leading icon for a spinner *and* stops the row accepting taps. The one
 *   row that kicks off async work (data export) had been re-deriving that pairing itself, and
 *   getting only half of it right would mean a second export firing on a double tap.
 */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContentDescription: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    RowFrame(onClick = if (enabled && !loading) onClick else null, modifier = modifier) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(MuhabbetSizes.IconLarge),
                strokeWidth = LoadingStrokeWidth
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconContentDescription,
                tint = accent,
                modifier = Modifier.size(MuhabbetSizes.IconLarge)
            )
        }
        RowLabels(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.weight(1f),
            titleColor = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * A row carrying a switch.
 *
 * The whole row toggles, not just the switch — both previous implementations only accepted a tap on
 * the switch itself.
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    RowFrame(onClick = if (enabled) ({ onCheckedChange(!checked) }) else null, modifier = modifier) {
        RowLabels(title, subtitle, Modifier.weight(1f))
        MuhabbetSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/**
 * One option in a radio group.
 *
 * Both previous call sites duplicated the selection body verbatim on the `Row` *and* on the
 * `RadioButton`, so every option carried its side effects twice — including, in the language
 * picker, an app restart.
 */
@Composable
fun SettingsRadioRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val haptics = LocalHaptics.current
    val select = {
        haptics.perform(MuhabbetHapticIntent.SegmentAdvanced)
        onSelect()
    }
    RowFrame(onClick = select, modifier = modifier) {
        RadioButton(selected = selected, onClick = select)
        RowLabels(title, subtitle, Modifier.weight(1f))
    }
}

/**
 * A row that reports something rather than doing something: a label, optionally an explanation, and
 * optionally the value it carries. Not clickable — that is the whole point of it existing next to
 * [SettingsNavRow].
 */
@Composable
fun SettingsInfoRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    icon: ImageVector? = null
) {
    RowFrame(onClick = null, modifier = modifier) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(MuhabbetSizes.IconLarge)
            )
        }
        RowLabels(title, subtitle, Modifier.weight(1f))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val LoadingStrokeWidth = MuhabbetSizes.ProgressStrokeThin
