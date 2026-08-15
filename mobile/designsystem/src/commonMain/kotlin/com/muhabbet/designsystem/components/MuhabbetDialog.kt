package com.muhabbet.designsystem.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

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
        title = { Text(title) },
        text = content,
        confirmButton = {
            if (confirmLabel != null && onConfirm != null) {
                TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                    Text(
                        text = confirmLabel,
                        color = if (destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        dismissButton = dismissLabel?.let {
            {
                TextButton(onClick = onDismiss, enabled = dismissible) { Text(it) }
            }
        }
    )
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
