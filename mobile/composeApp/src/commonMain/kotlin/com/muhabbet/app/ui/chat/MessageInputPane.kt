package com.muhabbet.app.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Poll
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
import com.muhabbet.app.ui.theme.MuhabbetElevation
import com.muhabbet.app.ui.theme.MuhabbetSpacing

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
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = stringResource(Res.string.chat_context_reply),
                modifier = Modifier.size(16.dp),
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
            IconButton(onClick = onCancel, modifier = Modifier.size(com.muhabbet.app.ui.theme.MuhabbetSizes.MinTouchTarget)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close), modifier = Modifier.size(16.dp))
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
                imageVector = Icons.Default.Edit,
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
            IconButton(onClick = onCancel, modifier = Modifier.size(com.muhabbet.app.ui.theme.MuhabbetSizes.MinTouchTarget)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

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
    onCameraPick: () -> Unit = {}
) {
    var showAttachMenu by remember { mutableStateOf(false) }

    Surface(tonalElevation = MuhabbetElevation.Level2) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji button (opens system keyboard emoji)
            IconButton(
                onClick = onGifPick,
                enabled = !isUploading && !isEditing
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEmotions,
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
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
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
                        leadingIcon = { Icon(Icons.Default.Image, contentDescription = stringResource(Res.string.attach_image), modifier = Modifier.size(20.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_document)) },
                        onClick = { showAttachMenu = false; onFilePick() },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = stringResource(Res.string.attach_document), modifier = Modifier.size(20.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_poll)) },
                        onClick = { showAttachMenu = false; onPollCreate() },
                        leadingIcon = { Icon(Icons.Default.Poll, contentDescription = stringResource(Res.string.attach_poll), modifier = Modifier.size(20.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_location)) },
                        onClick = { showAttachMenu = false; onLocationShare() },
                        leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = stringResource(Res.string.attach_location), modifier = Modifier.size(20.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_gif)) },
                        onClick = { showAttachMenu = false; onGifPick() },
                        leadingIcon = { Icon(Icons.Default.Gif, contentDescription = stringResource(Res.string.attach_gif), modifier = Modifier.size(20.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attach_camera)) },
                        onClick = { showAttachMenu = false; onCameraPick() },
                        leadingIcon = { Icon(Icons.Default.CameraAlt, contentDescription = stringResource(Res.string.attach_camera), modifier = Modifier.size(20.dp)) }
                    )
                }
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(Res.string.chat_message_placeholder)) },
                modifier = Modifier.weight(1f).testTag("message_input"),
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send)
            )

            Spacer(Modifier.width(MuhabbetSpacing.XSmall))

            if (messageText.isBlank() && !isEditing) {
                FilledIconButton(
                    onClick = onMicClick,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Mic, contentDescription = stringResource(Res.string.chat_voice_message), modifier = Modifier.size(20.dp))
                }
            } else {
                FilledIconButton(
                    onClick = onSend,
                    enabled = messageText.isNotBlank(),
                    modifier = Modifier.size(48.dp).testTag("send_button"),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isEditing) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary,
                        contentColor = if (isEditing) MaterialTheme.colorScheme.onTertiary
                        else MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isEditing) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(if (isEditing) Res.string.action_save else Res.string.action_send),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
