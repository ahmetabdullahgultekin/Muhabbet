package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetMotion
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing

/**
 * What a button means, which decides both how it looks and how it feels.
 *
 * Tying the haptic to the *role* rather than to the call site is the point: a destructive confirm
 * should feel different from an ordinary one, and no screen should have to remember that.
 */
enum class MuhabbetButtonRole {
    /** The main action on the screen. Filled. */
    Primary,

    /** A secondary path. Outlined. */
    Secondary,

    /** Text-only, for dialog dismissals and tertiary actions. */
    Text,

    /** Deletes, leaves, revokes. Filled in the error colour, and it buzzes differently. */
    Destructive,
}

/**
 * A button that presses back.
 *
 * Carries the press spring and the haptic so the 19 button call sites do not each have to — and
 * before this, none of them did: the app had zero haptics anywhere.
 *
 * The scale is a spatial spring rather than a duration, so an interrupted press settles instead of
 * snapping back.
 *
 * **Long labels** (#771): a label is centred on every line and stops at two lines with an ellipsis.
 * Before, it inherited `Text`'s defaults — start-aligned and unbounded — so at 2.0× font scale the
 * second line hugged the left edge of a symmetric pill, and nothing stopped a label from growing the
 * button without limit. The button keeps its 48dp minimum height and grows with the second line.
 *
 * @param leadingIcon drawn before the label; the icon and the label are centred together as one
 *   group, whatever the label's line count.
 * @param content replaces the label entirely, for the rare button that is not icon-plus-text. It
 *   takes over the whole policy above, so prefer [leadingIcon] when that is all that is needed.
 */
@Composable
fun MuhabbetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: MuhabbetButtonRole = MuhabbetButtonRole.Primary,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else 1f,
        animationSpec = MuhabbetMotion.spatialFast(),
        label = "buttonPress"
    )
    val pressModifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }

    val handleClick = {
        haptics.perform(
            if (role == MuhabbetButtonRole.Destructive) MuhabbetHapticIntent.DestructiveConfirmed
            else MuhabbetHapticIntent.MessageSent
        )
        onClick()
    }
    val label: @Composable RowScope.() -> Unit = content ?: { ButtonLabel(text, leadingIcon) }

    when (role) {
        MuhabbetButtonRole.Primary -> Button(
            onClick = handleClick,
            modifier = pressModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            content = label
        )

        MuhabbetButtonRole.Destructive -> Button(
            onClick = handleClick,
            modifier = pressModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            content = label
        )

        MuhabbetButtonRole.Secondary -> OutlinedButton(
            onClick = handleClick,
            modifier = pressModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            content = label
        )

        MuhabbetButtonRole.Text -> TextButton(
            onClick = handleClick,
            modifier = pressModifier,
            enabled = enabled,
            interactionSource = interactionSource,
            content = label
        )
    }
}

/**
 * The default button content: an optional icon and a centred label of at most [ButtonLabelMaxLines].
 *
 * The `Row` the Material button lays this out in already centres its children horizontally, so the
 * icon and the text are centred as a group; `TextAlign.Center` is what centres the *lines* within
 * the text once it wraps.
 */
@Composable
private fun RowScope.ButtonLabel(text: String, leadingIcon: ImageVector?) {
    if (leadingIcon != null) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            modifier = Modifier.size(MuhabbetSizes.IconSmall)
        )
        Spacer(Modifier.width(MuhabbetSpacing.Small))
    }
    Text(
        text = text,
        textAlign = TextAlign.Center,
        maxLines = ButtonLabelMaxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * A switch that reports which way it went.
 *
 * `ToggleOn` and `ToggleOff` are distinct haptics, so turning something off feels different from
 * turning it on without looking. Eight `Switch` call sites get this for free.
 *
 * @param onCheckedChange null when a parent owns the interaction (a toggleable row); the switch is
 *   then drawn but not separately focusable, so a screen reader meets one control, not two.
 */
@Composable
fun MuhabbetSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHaptics.current
    Switch(
        checked = checked,
        // Null makes the switch a pure indicator — not focusable, not clickable — for a parent that
        // owns the toggle itself, as SettingsSwitchRow does (#837). The haptic then belongs to that
        // parent, which is why it is only attached when there is a callback to attach it to.
        onCheckedChange = onCheckedChange?.let { change ->
            { checkedNow: Boolean ->
                haptics.perform(toggleHaptic(checkedNow))
                change(checkedNow)
            }
        },
        modifier = modifier,
        enabled = enabled
    )
}

/** The haptic for a toggle landing in [checked]: on and off feel different on purpose. */
internal fun toggleHaptic(checked: Boolean): MuhabbetHapticIntent =
    if (checked) MuhabbetHapticIntent.ToggleOn else MuhabbetHapticIntent.ToggleOff

/**
 * The one divider.
 *
 * 55 `HorizontalDivider` call sites disagreed about indentation — some full-bleed, some inset to
 * clear an avatar, some inset by an arbitrary amount. [startIndent] names the intent instead: a
 * divider between rows that have a leading avatar should start where the text starts, not under
 * the avatar, or the list reads as a table.
 */
@Composable
fun MuhabbetDivider(
    modifier: Modifier = Modifier,
    startIndent: Dp = MuhabbetSpacing.None
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

private const val PressedScale = 0.97f

/** Two lines and no more: a third line would make the button taller than the content it ends. */
internal const val ButtonLabelMaxLines = 2
