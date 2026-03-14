package com.muhabbet.app.ui.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun ChatLockDialog(
    isLocked: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = if (isLocked) stringResource(Res.string.chat_unlock)
        else stringResource(Res.string.chat_lock)
    val confirmLabel = if (isLocked) stringResource(Res.string.chat_unlock)
        else stringResource(Res.string.chat_lock)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) {
                Text(
                    text = confirmLabel,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(Res.string.cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
