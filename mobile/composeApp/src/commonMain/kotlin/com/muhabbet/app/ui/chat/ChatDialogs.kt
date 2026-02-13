package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import org.jetbrains.compose.resources.stringResource

@Composable
fun FullImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier.fillMaxSize().clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            coil3.compose.AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
    }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chat_forward_title)) },
        text = {
            if (forwardConversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            com.muhabbet.app.ui.components.UserAvatar(
                                avatarUrl = conv.avatarUrl,
                                displayName = convName,
                                size = 36.dp,
                                isGroup = conv.type == com.muhabbet.shared.model.ConversationType.GROUP
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(convName, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelText) }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.chat_delete_title)) },
        text = { Text(stringResource(Res.string.chat_delete_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.disappear_title)) },
        text = {
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
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
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

    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = { Text(stringResource(Res.string.location_share_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = locationLabel,
                    onValueChange = { locationLabel = it },
                    placeholder = { Text(stringResource(Res.string.location_label_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = locationLat,
                    onValueChange = { locationLat = it },
                    placeholder = { Text(stringResource(Res.string.location_lat_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = locationLng,
                    onValueChange = { locationLng = it },
                    placeholder = { Text(stringResource(Res.string.location_lng_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
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
                enabled = locationLat.toDoubleOrNull() != null && locationLng.toDoubleOrNull() != null
            ) { Text(stringResource(Res.string.poll_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.poll_create_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = pollQuestion,
                    onValueChange = { pollQuestion = it },
                    placeholder = { Text(stringResource(Res.string.poll_question_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                pollOptions.forEachIndexed { index, option ->
                    OutlinedTextField(
                        value = option,
                        onValueChange = { newVal ->
                            pollOptions = pollOptions.toMutableList().also { it[index] = newVal }
                        },
                        placeholder = { Text(stringResource(Res.string.poll_option_placeholder, index + 1)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    )
                }
                if (pollOptions.size < 6) {
                    TextButton(onClick = { pollOptions = pollOptions + "" }) {
                        Text(stringResource(Res.string.poll_add_option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val q = pollQuestion.trim()
                    val opts = pollOptions.map { it.trim() }.filter { it.isNotEmpty() }
                    if (q.isNotEmpty() && opts.size >= 2) {
                        onSend(PollData(question = q, options = opts))
                    }
                },
                enabled = pollQuestion.isNotBlank() && pollOptions.count { it.isNotBlank() } >= 2
            ) { Text(stringResource(Res.string.poll_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
        }
    )
}
