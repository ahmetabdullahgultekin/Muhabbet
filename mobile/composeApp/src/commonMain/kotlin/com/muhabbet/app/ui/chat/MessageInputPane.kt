package com.muhabbet.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.muhabbet.shared.model.Message
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.Muhabbet
import androidx.compose.animation.togetherWith
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent

@Composable
fun ReplyPreviewBar(
    replyingTo: Message,
    onCancel: () -> Unit
) {
    Surface(tonalElevation = MuhabbetElevation.Level4) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Medium, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Muhabbet.icons.Reply,
                contentDescription = stringResource(Res.string.chat_context_reply),
                modifier = Modifier.size(MuhabbetSizes.IconSmall),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(MuhabbetSpacing.Small))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.chat_replying_to),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = replyingTo.content.take(50),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(com.muhabbet.designsystem.theme.MuhabbetSizes.MinTouchTarget)) {
                Icon(Muhabbet.icons.Close, contentDescription = stringResource(Res.string.action_close), modifier = Modifier.size(MuhabbetSizes.IconSmall))
            }
        }
    }
}

@Composable
fun EditModeBar(
    editModeText: String,
    onCancel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = MuhabbetElevation.Level4
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Medium, vertical = MuhabbetSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Muhabbet.icons.Edit,
                contentDescription = stringResource(Res.string.chat_context_edit),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(MuhabbetSpacing.Small))
            Text(
                text = editModeText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCancel, modifier = Modifier.size(com.muhabbet.designsystem.theme.MuhabbetSizes.MinTouchTarget)) {
                Icon(Muhabbet.icons.Close, contentDescription = stringResource(Res.string.action_close), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageInputBar(
    messageText: String,
    onTextChange: (String) -> Unit,
    isEditing: Boolean,
    isUploading: Boolean,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onImagePick: () -> Unit,
    onFilePick: () -> Unit,
    onPollCreate: () -> Unit,
    onLocationShare: () -> Unit,
    onGifPick: () -> Unit = {},
    onStickerPick: () -> Unit = {},
    onCameraPick: () -> Unit = {},
    viewOnceEnabled: Boolean = false,
    onViewOnceToggle: () -> Unit = {},
    onVideoRecord: () -> Unit = {},
    onScheduleSend: () -> Unit = {}
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Surface(
        color = LocalSemanticColors.current.inputBarBackground,
        tonalElevation = MuhabbetElevation.None
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sticker shortcut — opens the shared GIF/sticker sheet on its Stickers tab.
            // (It used to be labelled a sticker button, drawn as a smiley, and wired to the GIF
            // tab, which is also what the attach menu's GIF entry below already does. Emoji
            // themselves need no button: the system keyboard supplies them in the text field.)
            IconButton(
                onClick = onStickerPick,
                enabled = !isUploading && !isEditing
            ) {
                Icon(
                    imageVector = Muhabbet.icons.Emoji,
                    contentDescription = stringResource(Res.string.attach_sticker),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Attach button with menu
            Box {
                IconButton(
                    onClick = { showAttachMenu = true },
                    enabled = !isUploading && !isEditing
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.size(MuhabbetSizes.IconLarge), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Muhabbet.icons.Attach,
                            contentDescription = stringResource(Res.string.attach_file),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_image)) },
                        onClick = { showAttachMenu = false; onImagePick() },
                        leadingIcon = { Icon(Muhabbet.icons.Image, contentDescription = stringResource(Res.string.attach_image), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_document)) },
                        onClick = { showAttachMenu = false; onFilePick() },
                        leadingIcon = { Icon(Muhabbet.icons.Document, contentDescription = stringResource(Res.string.attach_document), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_poll)) },
                        onClick = { showAttachMenu = false; onPollCreate() },
                        leadingIcon = { Icon(Muhabbet.icons.Poll, contentDescription = stringResource(Res.string.attach_poll), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_location)) },
                        onClick = { showAttachMenu = false; onLocationShare() },
                        leadingIcon = { Icon(Muhabbet.icons.Location, contentDescription = stringResource(Res.string.attach_location), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_gif)) },
                        onClick = { showAttachMenu = false; onGifPick() },
                        leadingIcon = { Icon(Muhabbet.icons.Gif, contentDescription = stringResource(Res.string.attach_gif), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_camera)) },
                        onClick = { showAttachMenu = false; onCameraPick() },
                        leadingIcon = { Icon(Muhabbet.icons.Camera, contentDescription = stringResource(Res.string.attach_camera), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.video_message)) },
                        onClick = { showAttachMenu = false; onVideoRecord() },
                        leadingIcon = { Icon(Muhabbet.icons.Video, contentDescription = stringResource(Res.string.video_message), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                    )
                }
            }

            // View-once toggle
            IconButton(
                onClick = onViewOnceToggle,
                enabled = !isEditing
            ) {
                Icon(
                    imageVector = if (viewOnceEnabled) Muhabbet.icons.Visible else Muhabbet.icons.Hidden,
                    contentDescription = stringResource(Res.string.view_once_label),
                    tint = if (viewOnceEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(Res.string.chat_message_placeholder)) },
                modifier = Modifier.weight(1f).testTag("message_input"),
                maxLines = 4,
                shape = RoundedCornerShape(MuhabbetCorners.Pill),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
            )

            Spacer(Modifier.width(MuhabbetSpacing.XSmall))

            // The mic and the send button occupy the same spot and swap as soon as the field has
            // text, which was an instant cut. A scale crossfade makes it read as one control
            // changing what it does, which is what it is. Spatial: it is a size change.
            AnimatedContent(
                targetState = messageText.isBlank() && !isEditing,
                transitionSpec = {
                    (scaleIn(Muhabbet.motion.spatialFast()) + fadeIn(Muhabbet.motion.effectsFast()))
                        .togetherWith(
                            scaleOut(Muhabbet.motion.effectsFast()) + fadeOut(Muhabbet.motion.effectsFast())
                        )
                },
                label = "micSendMorph"
            ) { showMic ->
            if (showMic) {
                FilledIconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(MuhabbetSizes.MinTouchTarget),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Muhabbet.icons.Mic, contentDescription = stringResource(Res.string.chat_voice_message), modifier = Modifier.size(MuhabbetSizes.IconMedium))
                }
            } else {
                // Tap = send now; long-press (text messages only) = schedule for later.
                val sendDescription = stringResource(if (isEditing) Res.string.action_save else Res.string.action_send)
                val scheduleDescription = stringResource(Res.string.schedule_send_action)
                Surface(
                    shape = CircleShape,
                    color = if (isEditing) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(MuhabbetSizes.MinTouchTarget)
                        .testTag("send_button")
                        .combinedClickable(
                            enabled = messageText.isNotBlank(),
                            onClickLabel = sendDescription,
                            onLongClickLabel = scheduleDescription,
                            onLongClick = if (!isEditing) onScheduleSend else null,
                            onClick = onSend
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isEditing) Muhabbet.icons.Sent else Muhabbet.icons.Send,
                            contentDescription = sendDescription,
                            tint = if (isEditing) MaterialTheme.colorScheme.onTertiary
                            else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(MuhabbetSizes.IconMedium)
                        )
                    }
                }
            }
            }
        }
    }
}
