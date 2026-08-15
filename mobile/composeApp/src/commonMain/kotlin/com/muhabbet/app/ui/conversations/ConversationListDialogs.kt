package com.muhabbet.app.ui.conversations

import com.muhabbet.designsystem.components.ConfirmDialog
import androidx.compose.runtime.Composable
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.composeapp.generated.resources.*

/*
 * Every dialog the conversation list can raise, in one place. Split out of
 * ConversationListScreen for the same reason as the body.
 */

/**
 * Hosts all four dialogs of [ConversationListScreen] (status create, long-press actions,
 * delete confirm, mute picker). Pure wiring — visibility and side effects are driven by the caller.
 */
@Composable
internal fun ConversationListDialogs(
    showStatusInput: Boolean,
    statusText: String,
    statusPickedImage: PickedImage?,
    isUploadingStatus: Boolean,
    onStatusTextChange: (String) -> Unit,
    onPickStatusImage: () -> Unit,
    onPostStatus: () -> Unit,
    onDismissStatus: () -> Unit,
    longPressTargetConv: ConversationResponse?,
    actionLabels: ConversationActionLabels,
    onPinToggle: (ConversationResponse) -> Unit,
    onArchiveToggle: (ConversationResponse) -> Unit,
    onMuteToggle: (ConversationResponse) -> Unit,
    onLockToggle: (ConversationResponse) -> Unit,
    onDeleteFromMenu: (ConversationResponse) -> Unit,
    onDismissMenu: () -> Unit,
    deleteTargetConv: ConversationResponse?,
    deleteTitle: String,
    deleteMessage: String,
    onConfirmDelete: (ConversationResponse) -> Unit,
    onDismissDelete: () -> Unit,
    showMuteDialog: Boolean,
    onMuteDuration: (String) -> Unit,
    onDismissMute: () -> Unit
) {
    if (showStatusInput) {
        StatusCreateDialog(
            statusText = statusText,
            pickedImage = statusPickedImage,
            isUploading = isUploadingStatus,
            cancelLabel = actionLabels.cancel,
            onTextChange = onStatusTextChange,
            onPickImage = onPickStatusImage,
            onPost = onPostStatus,
            onDismiss = onDismissStatus
        )
    }

    longPressTargetConv?.let { conv ->
        ConversationActionsDialog(
            conversation = conv,
            pinLabel = actionLabels.pin,
            unpinLabel = actionLabels.unpin,
            archiveLabel = actionLabels.archive,
            unarchiveLabel = actionLabels.unarchive,
            muteLabel = actionLabels.mute,
            unmuteLabel = actionLabels.unmute,
            lockLabel = actionLabels.lock,
            unlockLabel = actionLabels.unlock,
            deleteLabel = actionLabels.delete,
            cancelLabel = actionLabels.cancel,
            onPinToggle = { onPinToggle(conv) },
            onArchiveToggle = { onArchiveToggle(conv) },
            onMuteToggle = { onMuteToggle(conv) },
            onLockToggle = { onLockToggle(conv) },
            onDelete = { onDeleteFromMenu(conv) },
            onDismiss = onDismissMenu
        )
    }

    deleteTargetConv?.let { conv ->
        ConfirmDialog(
            title = deleteTitle,
            message = deleteMessage,
            confirmLabel = actionLabels.delete,
            onConfirm = { onConfirmDelete(conv) },
            onDismiss = onDismissDelete,
            isDestructive = true,
            dismissLabel = actionLabels.cancel
        )
    }

    if (showMuteDialog) {
        MutePickerDialog(
            onDismiss = onDismissMute,
            onMuteDuration = onMuteDuration
        )
    }
}
