package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.ConversationResponse

/**
 * Long-press context menu for a conversation row (pin/archive/mute/lock/delete).
 * Pure UI — all side effects are delegated to the supplied callbacks by [ConversationListScreen].
 */
@Composable
internal fun ConversationActionsDialog(
    conversation: ConversationResponse,
    pinLabel: String,
    unpinLabel: String,
    archiveLabel: String,
    unarchiveLabel: String,
    muteLabel: String,
    unmuteLabel: String,
    lockLabel: String,
    unlockLabel: String,
    deleteLabel: String,
    cancelLabel: String,
    onPinToggle: () -> Unit,
    onArchiveToggle: () -> Unit,
    onMuteToggle: () -> Unit,
    onLockToggle: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(conversation.name ?: "") },
        text = {
            Column {
                ActionRow(
                    icon = Icons.Default.PushPin,
                    iconTint = MaterialTheme.colorScheme.onSurface,
                    label = if (conversation.isPinned) unpinLabel else pinLabel,
                    onClick = onPinToggle
                )
                ActionRow(
                    label = if (conversation.isArchived) unarchiveLabel else archiveLabel,
                    onClick = onArchiveToggle
                )
                ActionRow(
                    label = if (conversation.isMuted) unmuteLabel else muteLabel,
                    onClick = onMuteToggle
                )
                ActionRow(
                    label = if (conversation.isLocked) unlockLabel else lockLabel,
                    onClick = onLockToggle
                )
                ActionRow(
                    icon = Icons.Default.Close,
                    iconTint = MaterialTheme.colorScheme.error,
                    label = deleteLabel,
                    labelColor = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        }
    )
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    label: String,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = iconTint
            )
            Spacer(Modifier.width(MuhabbetSpacing.Large))
        } else {
            Spacer(Modifier.width(MuhabbetSpacing.XSmall))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor
        )
    }
}
