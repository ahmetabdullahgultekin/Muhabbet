package com.muhabbet.designsystem.components

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetSizes

/**
 * A checkbox that reports which way it went, like [MuhabbetSwitch]: `ToggleOn` when it becomes
 * checked, `ToggleOff` when it is cleared, so the hand can tell the two apart without looking.
 *
 * @param onCheckedChange null when a surrounding row owns the toggle (the whole row is the touch
 *   target, as [SettingsSwitchRow] does it). The box is then purely a display, takes no touch
 *   target of its own, and does not double-fire the row's haptic. Non-null, the control's own node
 *   is at least [MuhabbetSizes.MinTouchTarget] square.
 *
 * Why the size is set here rather than trusted to M3: M3 extends a checkbox's *hit area* to 48dp
 * but leaves its semantics node at the 24dp glyph, and accessibility scanners and TalkBack's focus
 * ring read the node. Measured in `CatalogueSemanticsTest` — the stock control reports 24dp.
 */
@Composable
fun MuhabbetCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHaptics.current
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange?.let { change ->
            { next: Boolean ->
                haptics.perform(if (next) MuhabbetHapticIntent.ToggleOn else MuhabbetHapticIntent.ToggleOff)
                change(next)
            }
        },
        modifier = modifier.touchTargetIf(onCheckedChange != null),
        enabled = enabled,
        // Named rather than inherited: checkmark-on-primary is a declared, measured pair
        // (`onPrimary/primary` in SemanticColorContrastTest), and the unchecked box is an outline
        // that carries information, so it takes `outline` (3:1), not the decorative variant.
        colors = CheckboxDefaults.colors(
            checkedColor = MaterialTheme.colorScheme.primary,
            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
            uncheckedColor = MaterialTheme.colorScheme.outline
        )
    )
}

/**
 * A radio button. Choosing it fires `SegmentAdvanced`, the same tick a filter chip gives, because
 * both are "one of several"; tapping the option that is already chosen is silent, because nothing
 * changed.
 *
 * @param onClick null when a surrounding row owns the choice — see [MuhabbetCheckbox], including
 *   why the touch target is enforced here.
 */
@Composable
fun MuhabbetRadio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHaptics.current
    RadioButton(
        selected = selected,
        onClick = onClick?.let { click ->
            {
                if (!selected) haptics.perform(MuhabbetHapticIntent.SegmentAdvanced)
                click()
            }
        },
        modifier = modifier.touchTargetIf(onClick != null),
        enabled = enabled,
        colors = RadioButtonDefaults.colors(
            selectedColor = MaterialTheme.colorScheme.primary,
            unselectedColor = MaterialTheme.colorScheme.outline
        )
    )
}

/** The minimum touch target, for a control that is itself the thing being tapped. */
private fun Modifier.touchTargetIf(interactive: Boolean): Modifier =
    if (interactive) sizeIn(minWidth = MuhabbetSizes.MinTouchTarget, minHeight = MuhabbetSizes.MinTouchTarget) else this
