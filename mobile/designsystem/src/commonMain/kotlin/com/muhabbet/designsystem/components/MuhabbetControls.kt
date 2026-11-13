package com.muhabbet.designsystem.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.muhabbet.designsystem.modifier.MuhabbetPress
import com.muhabbet.designsystem.modifier.pressScale
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.performingHaptic

/**
 * What a button means, which decides how it looks — and, for [Destructive] only, how it feels.
 *
 * Only the destructive role carries a haptic of its own, because "you just confirmed something you
 * cannot take back" is a UI fact the role already states. Every other role is silent unless the
 * caller names an intent: a Cancel, a Save and a Continue are not events the hand needs telling
 * about, and the old fallback that made all of them buzz `MessageSent` taught users to ignore the
 * channel altogether (#836).
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
 * Carries the press squeeze ([MuhabbetPress.Standard]) so no call site has to, and a haptic only
 * when one means something: see [MuhabbetButtonRole].
 *
 * @param haptic what the hand is told when the button fires. Defaults to
 *   [MuhabbetHapticIntent.DestructiveConfirmed] for [MuhabbetButtonRole.Destructive] and to nothing
 *   for every other role. A *domain* event — a message sent, a reaction applied — is never named
 *   here: it fires from the feature when the event is confirmed, not from the tap that requested it.
 */
@Composable
fun MuhabbetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: MuhabbetButtonRole = MuhabbetButtonRole.Primary,
    enabled: Boolean = true,
    haptic: MuhabbetHapticIntent? = MuhabbetButtonDefaults.haptic(role),
    content: (@Composable RowScope.() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressModifier = modifier.pressScale(interactionSource, MuhabbetPress.Standard)
    val handleClick = LocalHaptics.current.performingHaptic(haptic, onClick)
    val label: @Composable RowScope.() -> Unit = content ?: { Text(text) }

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
 * A switch that reports which way it went.
 *
 * `ToggleOn` and `ToggleOff` are distinct haptics, so turning something off feels different from
 * turning it on without looking. Eight `Switch` call sites get this for free.
 */
@Composable
fun MuhabbetSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptics = LocalHaptics.current
    Switch(
        checked = checked,
        onCheckedChange = {
            haptics.perform(
                if (it) MuhabbetHapticIntent.ToggleOn else MuhabbetHapticIntent.ToggleOff
            )
            onCheckedChange(it)
        },
        modifier = modifier,
        enabled = enabled
    )
}

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

/** The defaults [MuhabbetButton] derives from its role, named so a caller can reason about them. */
object MuhabbetButtonDefaults {
    /** [MuhabbetHapticIntent.DestructiveConfirmed] for a destructive button; nothing otherwise. */
    fun haptic(role: MuhabbetButtonRole): MuhabbetHapticIntent? =
        if (role == MuhabbetButtonRole.Destructive) MuhabbetHapticIntent.DestructiveConfirmed else null
}
