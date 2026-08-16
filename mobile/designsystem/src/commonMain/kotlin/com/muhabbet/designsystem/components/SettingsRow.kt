package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetMotion
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
 *
 * A clickable row also presses back a fraction, the same spatial spring every other pressable
 * control in the app uses, and a leading icon sits in a tinted tile rather than floating bare on
 * the row — this was the one component in the catalogue setting no colour, shape or motion of its
 * own, and it is the whole of the Settings surface.
 */
@Composable
private fun RowFrame(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    // Built only when the row is actually clickable: a row with no action (SettingsInfoRow) has no
    // press state to track, and this keeps it from paying for one every recomposition.
    val pressModifier = if (onClick != null) {
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) RowPressedScale else 1f,
            animationSpec = MuhabbetMotion.spatialFast(),
            label = "settingsRowPress"
        )
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MuhabbetSizes.MinTouchTarget)
            .then(pressModifier)
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
 * A leading icon sitting in its own tinted, rounded square rather than floating bare on the row —
 * the same "icon gets a considered container" move [MuhabbetScreenState] makes for its own icons,
 * so the two read as one family instead of two unrelated treatments.
 */
@Composable
private fun SettingsIconTile(
    icon: ImageVector,
    contentDescription: String?,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .size(MuhabbetSizes.SettingsIconTile)
            .clip(RoundedCornerShape(MuhabbetCorners.Small))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(MuhabbetSizes.IconLarge)
        )
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
    RowFrame(onClick = if (enabled && !loading) onClick else null, modifier = modifier) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(MuhabbetSizes.IconLarge),
                strokeWidth = LoadingStrokeWidth
            )
        } else if (icon != null) {
            SettingsIconTile(
                icon = icon,
                contentDescription = iconContentDescription,
                containerColor = if (destructive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                contentColor = if (destructive) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                }
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
            // Neutral, not accented: this row does nothing when tapped, and an accented tile would
            // promise otherwise.
            SettingsIconTile(
                icon = icon,
                contentDescription = null,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
private const val RowPressedScale = 0.98f
