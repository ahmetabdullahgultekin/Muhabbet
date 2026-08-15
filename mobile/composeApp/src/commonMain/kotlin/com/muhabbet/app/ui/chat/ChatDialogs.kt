package com.muhabbet.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.LocationData
import com.muhabbet.shared.dto.PollData
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.protocol.WsMessage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.components.ConfirmDialog
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetDialog
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetIconButton

/**
 * Full-screen media viewer with semi-transparent action bars.
 * Tap toggles UI overlay visibility (WhatsApp-style).
 */
@Composable
fun MediaViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
    onForward: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    var showOverlay by remember { mutableStateOf(true) }
    val semanticColors = LocalSemanticColors.current
    val forwardText = stringResource(Res.string.media_viewer_forward)
    val deleteText = stringResource(Res.string.media_viewer_delete)

    // Pinch-to-zoom and pan state
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) {
            Offset(
                x = offset.x + panChange.x,
                y = offset.y + panChange.y
            )
        } else Offset.Zero
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(semanticColors.scrim)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showOverlay = !showOverlay },
                        onDoubleTap = {
                            // Double-tap to toggle zoom
                            if (scale > 1.5f) {
                                scale = 1f; offset = Offset.Zero
                            } else {
                                scale = 3f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = stringResource(Res.string.a11y_image_full_screen),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .transformable(state = transformState),
                contentScale = ContentScale.Fit
            )

            // Top bar
            AnimatedVisibility(
                visible = showOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(semanticColors.scrimOverlay)
                        .padding(horizontal = MuhabbetSpacing.XSmall, vertical = MuhabbetSpacing.Small)
                ) {
                    MuhabbetIconButton(
                        icon = Muhabbet.icons.Close,
                        contentDescription = stringResource(Res.string.action_close),
                        onClick = onDismiss,
                        tint = semanticColors.onScrim
                    )
                }
            }

            // Bottom action bar
            AnimatedVisibility(
                visible = showOverlay && (onForward != null || onDelete != null),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(semanticColors.scrimOverlay)
                        .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onForward != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onForward() }
                        ) {
                            Icon(Muhabbet.icons.Forward, contentDescription = forwardText, tint = semanticColors.onScrim)
                            Text(forwardText, color = semanticColors.onScrim, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (onDelete != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { onDelete() }
                        ) {
                            Icon(Muhabbet.icons.Delete, contentDescription = deleteText, tint = semanticColors.onScrim)
                            Text(deleteText, color = semanticColors.onScrim, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

/** Backward-compatible wrapper — used by ChatScreen where no actions are needed yet */
@Composable
fun FullImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    MediaViewer(imageUrl = imageUrl, onDismiss = onDismiss)
}

@Composable
fun ForwardPickerDialog(
    forwardMessage: Message,
    forwardConversations: List<ConversationResponse>,
    conversationId: String,
    currentUserId: String,
    wsClient: WsClient,
    scope: CoroutineScope,
    errorSendMsg: String,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    onDismiss: () -> Unit,
    onNavigateToConversation: ((conversationId: String, name: String) -> Unit)? = null
) {
    val cancelText = stringResource(Res.string.cancel)
    MuhabbetDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.chat_forward_title),
        dismissLabel = cancelText,
        content ={
            if (forwardConversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(MuhabbetSizes.IconLarge))
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(forwardConversations.filter { it.id != conversationId }, key = { it.id }) { conv ->
                        val convName = conv.name
                            ?: conv.participants.firstOrNull { it.userId != currentUserId }?.displayName
                            ?: conv.participants.firstOrNull { it.userId != currentUserId }?.phoneNumber
                            ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val targetConv = conv
                                    val targetName = convName
                                    onDismiss()
                                    scope.launch {
                                        try {
                                            val messageId = generateMessageId()
                                            val requestId = generateMessageId()
                                            wsClient.send(
                                                WsMessage.SendMessage(
                                                    requestId = requestId,
                                                    messageId = messageId,
                                                    conversationId = targetConv.id,
                                                    content = forwardMessage.content,
                                                    contentType = forwardMessage.contentType,
                                                    mediaUrl = forwardMessage.mediaUrl,
                                                    thumbnailUrl = forwardMessage.thumbnailUrl,
                                                    forwardedFrom = forwardMessage.id
                                                )
                                            )
                                            onNavigateToConversation?.invoke(targetConv.id, targetName)
                                        } catch (_: Exception) {
                                            snackbarHostState.showSnackbar(errorSendMsg)
                                        }
                                    }
                                }
                                .padding(vertical = MuhabbetSpacing.Medium, horizontal = MuhabbetSpacing.Small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.muhabbet.designsystem.components.UserAvatar(
                                avatarUrl = conv.avatarUrl,
                                displayName = convName,
                                size = MuhabbetSizes.AvatarXSmall,
                                isGroup = conv.type == com.muhabbet.shared.model.ConversationType.GROUP,
                                contentDescription = if (conv.type == com.muhabbet.shared.model.ConversationType.GROUP) stringResource(Res.string.cd_group_avatar) else convName
                            )
                            Spacer(Modifier.width(MuhabbetSpacing.Medium))
                            Text(convName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    ConfirmDialog(
        title = stringResource(Res.string.chat_delete_title),
        message = stringResource(Res.string.chat_delete_confirm),
        confirmLabel = stringResource(Res.string.delete),
        dismissLabel = stringResource(Res.string.cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isDestructive = true
    )
}

@Composable
fun DisappearTimerDialog(
    currentSeconds: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val timerOptions = listOf(
        null to stringResource(Res.string.disappear_off),
        30 to stringResource(Res.string.disappear_30s),
        300 to stringResource(Res.string.disappear_5m),
        3600 to stringResource(Res.string.disappear_1h),
        86400 to stringResource(Res.string.disappear_1d),
        604800 to stringResource(Res.string.disappear_1w)
    )
    MuhabbetDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.disappear_title),
        dismissLabel = stringResource(Res.string.cancel),
        content ={
            Column {
                timerOptions.forEach { (seconds, label) ->
                    val isSelected = currentSeconds == seconds
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = MuhabbetSpacing.Medium, horizontal = MuhabbetSpacing.Small),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    )
}

@Composable
fun LocationShareDialog(
    onSend: (LocationData) -> Unit,
    onDismiss: () -> Unit
) {
    var locationLabel by remember { mutableStateOf("") }
    var locationLat by remember { mutableStateOf("") }
    var locationLng by remember { mutableStateOf("") }

    MuhabbetDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.location_share_title),
        dismissLabel = stringResource(Res.string.cancel),
        confirmLabel = stringResource(Res.string.poll_send),
        onConfirm = {
            val lat = locationLat.toDoubleOrNull()
            val lng = locationLng.toDoubleOrNull()
            if (lat != null && lng != null) {
                onSend(LocationData(
                    latitude = lat,
                    longitude = lng,
                    label = locationLabel.takeIf { it.isNotBlank() }
                ))
            }
        },
        confirmEnabled = locationLat.toDoubleOrNull() != null && locationLng.toDoubleOrNull() != null,
        content = {
            Column {
                MuhabbetTextField(
                    value = locationLabel,
                    onValueChange = { locationLabel = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.location_label_placeholder),
                    singleLine = true
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                MuhabbetTextField(
                    value = locationLat,
                    onValueChange = { locationLat = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.location_lat_placeholder),
                    singleLine = true
                )
                Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                MuhabbetTextField(
                    value = locationLng,
                    onValueChange = { locationLng = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.location_lng_placeholder),
                    singleLine = true
                )
            }
        }
    )
}

@Composable
fun PollCreateDialog(
    onSend: (PollData) -> Unit,
    onDismiss: () -> Unit
) {
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }

    MuhabbetDialog(
        onDismiss = onDismiss,
        title = stringResource(Res.string.poll_create_title),
        dismissLabel = stringResource(Res.string.cancel),
        confirmLabel = stringResource(Res.string.poll_send),
        onConfirm = {
                    val q = pollQuestion.trim()
                    val opts = pollOptions.map { it.trim() }.filter { it.isNotEmpty() }
                    if (q.isNotEmpty() && opts.size >= 2) {
                        onSend(PollData(question = q, options = opts))
                    }
                },
        confirmEnabled = pollQuestion.isNotBlank() && pollOptions.count { it.isNotBlank() } >= 2,
        content ={
            Column {
                MuhabbetTextField(
                    value = pollQuestion,
                    onValueChange = { pollQuestion = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.poll_question_placeholder),
                    singleLine = true
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                pollOptions.forEachIndexed { index, option ->
                    MuhabbetTextField(
                        value = option,
                        onValueChange = { newVal ->
                            pollOptions = pollOptions.toMutableList().also { it[index] = newVal }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        placeholder = stringResource(Res.string.poll_option_placeholder, index + 1),
                        singleLine = true
                    )
                }
                if (pollOptions.size < 6) {
                    MuhabbetButton(
                        text = stringResource(Res.string.poll_add_option),
                        onClick = { pollOptions = pollOptions + "" },
                        role = MuhabbetButtonRole.Text
                    )
                }
            }
        }
    )
}
