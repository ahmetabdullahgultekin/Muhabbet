package com.muhabbet.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.muhabbet.app.platform.AudioPlayer
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    audioPlayer: AudioPlayer,
    repliedMessage: Message? = null,
    isStarred: Boolean = false,
    showContextMenu: Boolean = false,
    onLongPress: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onReply: () -> Unit = {},
    onForward: () -> Unit = {},
    onStar: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onImageClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp,
                    bottomStart = if (isOwn) 16.dp else 4.dp,
                    bottomEnd = if (isOwn) 4.dp else 16.dp
                ),
                color = if (message.isDeleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else if (isOwn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isOwn) 0.dp else 1.dp,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .widthIn(min = 80.dp, max = 300.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress,
                        onDoubleClick = onDoubleTap
                    )
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    // Quoted reply
                    if (repliedMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOwn) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Row(modifier = Modifier.padding(8.dp)) {
                                Box(
                                    modifier = Modifier.width(3.dp).height(32.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = repliedMessage.content.take(60),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    if (message.isDeleted) {
                        Text(
                            text = stringResource(Res.string.chat_message_deleted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        // Voice
                        if (message.contentType == ContentType.VOICE && message.mediaUrl != null) {
                            VoiceBubble(
                                mediaUrl = message.mediaUrl!!,
                                durationSeconds = null,
                                isOwn = isOwn,
                                audioPlayer = audioPlayer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        // Document
                        if (message.contentType == ContentType.DOCUMENT && message.mediaUrl != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isOwn) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clickable { /* open URL */ }
                            ) {
                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(28.dp),
                                        tint = if (isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(message.content, style = MaterialTheme.typography.bodySmall,
                                        color = if (isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2)
                                }
                            }
                        }
                        // Location
                        if (message.contentType == ContentType.LOCATION) {
                            LocationBubble(content = message.content, isOwn = isOwn, modifier = Modifier.fillMaxWidth())
                        }
                        // Poll
                        if (message.contentType == ContentType.POLL) {
                            PollBubble(messageId = message.id, pollContent = message.content, isOwn = isOwn, modifier = Modifier.fillMaxWidth())
                        }
                        // GIF
                        if (message.contentType == ContentType.GIF && message.mediaUrl != null) {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        // Sticker
                        if (message.contentType == ContentType.STICKER && message.mediaUrl != null) {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = null,
                                modifier = Modifier.size(150.dp).padding(4.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        // Image
                        if (message.contentType == ContentType.IMAGE && (message.mediaUrl != null || message.thumbnailUrl != null)) {
                            AsyncImage(
                                model = message.thumbnailUrl ?: message.mediaUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { message.mediaUrl?.let { onImageClick(it) } },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        // Text
                        if (message.contentType == ContentType.TEXT ||
                            (message.contentType == ContentType.IMAGE && message.content != stringResource(Res.string.chat_photo) && message.content.isNotBlank())
                        ) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isOwn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            if (message.contentType == ContentType.TEXT) {
                                val firstUrl = extractFirstUrl(message.content)
                                if (firstUrl != null) {
                                    LinkPreviewCard(url = firstUrl, isOwn = isOwn)
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }

                    // Timestamp + edited + delivery status
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (message.editedAt != null && !message.isDeleted) {
                            Text(
                                text = stringResource(Res.string.chat_edited),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        val timestamp = message.serverTimestamp ?: message.clientTimestamp
                        Text(
                            text = formatMessageTime(timestamp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        if (isOwn && !message.isDeleted) {
                            val (icon, tint) = when (message.status) {
                                MessageStatus.SENDING -> Icons.Default.AccessTime to MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                MessageStatus.SENT -> Icons.Default.Check to MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll to MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                MessageStatus.READ -> Icons.Default.DoneAll to MaterialTheme.colorScheme.tertiary
                            }
                            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
                        }
                    }
                }
            }

            // Context menu
            DropdownMenu(expanded = showContextMenu, onDismissRequest = onDismissMenu) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.chat_context_reply)) },
                    onClick = onReply,
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.chat_context_forward)) },
                    onClick = onForward,
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp)) }
                )
                DropdownMenuItem(
                    text = { Text(if (isStarred) stringResource(Res.string.chat_context_unstar) else stringResource(Res.string.chat_context_star)) },
                    onClick = onStar,
                    leadingIcon = {
                        Icon(
                            if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null, modifier = Modifier.size(20.dp),
                            tint = if (isStarred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
                if (isOwn) {
                    if (message.contentType == ContentType.TEXT) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.chat_context_edit)) },
                            onClick = onEdit,
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.chat_context_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = onDelete,
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

internal fun formatMessageTime(instant: kotlinx.datetime.Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

internal fun generateMessageId(): String {
    val chars = "0123456789abcdef"
    return buildString {
        repeat(8) { append(chars.random()) }; append('-')
        repeat(4) { append(chars.random()) }; append('-'); append('4')
        repeat(3) { append(chars.random()) }; append('-')
        append(listOf('8', '9', 'a', 'b').random())
        repeat(3) { append(chars.random()) }; append('-')
        repeat(12) { append(chars.random()) }
    }
}
