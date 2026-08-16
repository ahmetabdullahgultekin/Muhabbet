package com.muhabbet.designsystem.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.muhabbet.designsystem.theme.LocalHaptics
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetMotion

/**
 * The one dialog.
 *
 * Thirteen `AlertDialog` call sites agreed on almost everything and differed by accident. Twelve of
 * them ended in a literal `TextButton(onClick = onDismiss) { Text(cancel) }`; four passed
 * `confirmButton = {}` to satisfy a required parameter they had no use for; and one put its only
 * button in `confirmButton` rather than `dismissButton`, which silently moved it to the other side
 * of the dialog. All three of those become unexpressible here: buttons are described by label, and
 * a dialog with nothing to confirm simply has no confirm label.
 *
 * The corner comes from [MuhabbetCorners.Large] rather than M3's own default (`extraLarge`, 28dp) —
 * a touch less round reads as a considered choice instead of the stock Material silhouette every
 * `AlertDialog` in every other app has, and it matches [MuhabbetBottomSheet]'s corner so the two
 * modal surfaces in the app read as one family. Its buttons carry the same press spring every other
 * pressable control does, and a destructive confirm buzzes — the same haptic a destructive
 * `MuhabbetButton` uses — because confirming a delete is the one dialog action this catalogue
 * already names an intent for.
 *
 * @param dismissible whether the dialog can be dismissed *at all* right now. It gates the scrim tap,
 *   the back gesture and the dismiss button together. The status composer guarded only two of the
 *   three by hand, so an upload in flight could still be cancelled out from under itself.
 * @param content the body, passed through to the dialog untouched. Deliberately not wrapped in a
 *   `Column` here: every caller already supplies its own layout, and imposing one would have meant
 *   re-indenting thirteen bodies in the same commit that changed their surrounding API — exactly the
 *   kind of diff a behaviour change hides in.
 */
@Composable
fun MuhabbetDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissible: Boolean = true,
    content: @Composable () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        modifier = modifier,
        shape = RoundedCornerShape(MuhabbetCorners.Large),
        title = { Text(title) },
        text = content,
        confirmButton = {
            if (confirmLabel != null && onConfirm != null) {
                DialogTextButton(
                    label = confirmLabel,
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    hapticIntent = if (destructive) MuhabbetHapticIntent.DestructiveConfirmed else null
                )
            }
        },
        dismissButton = dismissLabel?.let {
            {
                DialogTextButton(
                    label = it,
                    onClick = onDismiss,
                    enabled = dismissible,
                    color = MaterialTheme.colorScheme.primary,
                    hapticIntent = null
                )
            }
        }
    )
}

/**
 * A dialog's text button, pressing back the same spatial spring every other control in the app
 * uses. Kept private: it is a detail of how [MuhabbetDialog] renders its two buttons, not a general
 * button variant — that is [MuhabbetButton]'s job.
 */
@Composable
private fun DialogTextButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    color: Color,
    hapticIntent: MuhabbetHapticIntent?
) {
    val haptics = LocalHaptics.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) DialogButtonPressedScale else 1f,
        animationSpec = MuhabbetMotion.spatialFast(),
        label = "dialogButtonPress"
    )
    TextButton(
        onClick = {
            hapticIntent?.let { haptics.perform(it) }
            onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    ) {
        Text(text = label, color = color)
    }
}

/**
 * A dialog whose body is a single sentence — delete, leave and logout confirmations.
 *
 * Kept as its own entry point rather than folded into [MuhabbetDialog]: at ten call sites the
 * message-plus-two-buttons shape is worth naming, and the caller passing a `String` instead of a
 * composable is the difference between a confirmation and an arbitrary form.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false
) {
    MuhabbetDialog(
        title = title,
        onDismiss = onDismiss,
        dismissLabel = dismissLabel,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        destructive = isDestructive
    ) {
        Text(message)
    }
}

private const val DialogButtonPressedScale = 0.95f
