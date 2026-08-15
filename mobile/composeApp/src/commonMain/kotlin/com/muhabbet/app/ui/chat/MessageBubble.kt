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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil3.compose.AsyncImage
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.muhabbet.app.platform.AudioPlayer
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.Muhabbet
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedContent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    audioPlayer: AudioPlayer,
    repliedMessage: Message? = null,
    isStarred: Boolean = false,
    showContextMenu: Boolean = false,
    senderName: String? = null,
    onLongPress: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onReply: () -> Unit = {},
    onForward: () -> Unit = {},
    onStar: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onReactionToggle: (String) -> Unit = {},
    onInfo: () -> Unit = {},
    onCopy: () -> Unit = {},
    onViewOnce: (String) -> Unit = {},
    // Documents, link previews and shared locations all end in "hand this URL to the platform".
    // The bubble does not do it itself: opening can fail (nothing installed that handles the URL),
    // and the snackbar that has to say so lives on the screen.
    onOpenUrl: (String) -> Unit = {}
) {
    // Dispatch view-once messages to ViewOnceBubble
    if (message.viewOnce && !message.isDeleted) {
        ViewOnceBubble(
            message = message,
            isOwn = isOwn,
            // Recipients mark the message viewed server-side the first time they open it.
            // Senders never consume their own view-once; the backend rejects self-view.
            onViewOnce = { if (!isOwn) onViewOnce(message.id) },
            modifier = Modifier
        )
        return
    }

    val clipboardManager = LocalClipboardManager.current
    val semanticColors = LocalSemanticColors.current
    val bubbleColor = if (message.isDeleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else if (isOwn) semanticColors.bubbleOwn
        else semanticColors.bubbleOther
    val onBubbleColor = if (isOwn) semanticColors.onBubbleOwn else semanticColors.onBubbleOther
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(MuhabbetCorners.Bubble),
                color = bubbleColor,
                tonalElevation = MuhabbetElevation.None,
                shadowElevation = MuhabbetElevation.Level1,
                modifier = Modifier
                    .widthIn(min = MuhabbetSizes.BubbleMinWidth, max = MuhabbetSizes.BubbleMaxWidth)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress,
                        onDoubleClick = onDoubleTap
                    )
            ) {
                Column(modifier = Modifier.padding(
                    horizontal = MuhabbetSizes.BubblePaddingHorizontal,
                    vertical = MuhabbetSizes.BubblePaddingVertical
                )) {
                    // Sender name for group messages
                    if (senderName != null && !isOwn) {
                        Text(
                            text = senderName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSizes.GapHairline)
                        )
                    }
                    // Quoted reply
                    if (repliedMessage != null) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = onBubbleColor.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.XSmall, vertical = MuhabbetSizes.GapHairline)
                        ) {
                            Row(modifier = Modifier.padding(MuhabbetSpacing.Small)) {
                                Box(
                                    modifier = Modifier.width(MuhabbetSizes.QuoteBarWidth).height(MuhabbetSizes.QuoteBarHeight)
                                        .clip(RoundedCornerShape(MuhabbetCorners.Hairline))
                                )
                                Column(modifier = Modifier.padding(start = MuhabbetSpacing.Small)) {
                                    Text(
                                        text = repliedMessage.content.take(60),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = onBubbleColor.copy(alpha = 0.8f),
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    // Forwarded label
                    if (message.forwardedFrom != null) {
                        Row(
                            modifier = Modifier.padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSizes.GapHairline),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)
                        ) {
                            Icon(
                                Muhabbet.icons.Send,
                                contentDescription = stringResource(Res.string.chat_forwarded),
                                modifier = Modifier.size(MuhabbetSizes.IconInline),
                                tint = onBubbleColor.copy(alpha = 0.8f)
                            )
                            Text(
                                text = stringResource(Res.string.chat_forwarded),
                                style = Muhabbet.text.ChatForwardedLabel,
                                color = onBubbleColor.copy(alpha = 0.8f),
                            )
                        }
                    }

                    if (message.isDeleted) {
                        Text(
                            text = stringResource(Res.string.chat_message_deleted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSpacing.XSmall)
                        )
                    } else {
                        // Voice
                        if (message.contentType == ContentType.VOICE && message.mediaUrl != null) {
                            VoiceBubble(
                                mediaUrl = message.mediaUrl ?: return@Column,
                                durationSeconds = null,
                                isOwn = isOwn,
                                audioPlayer = audioPlayer,
                                modifier = Modifier.padding(horizontal = MuhabbetSpacing.XSmall, vertical = MuhabbetSizes.GapHairline)
                            )
                        }
                        // Document
                        if (message.contentType == ContentType.DOCUMENT && message.mediaUrl != null) {
                            DocumentBubble(
                                fileName = message.content,
                                onBubbleColor = onBubbleColor,
                                onOpen = { message.mediaUrl?.let(onOpenUrl) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.XSmall, vertical = MuhabbetSizes.GapHairline)
                            )
                        }
                        // Location
                        if (message.contentType == ContentType.LOCATION) {
                            LocationBubble(content = message.content, isOwn = isOwn, onOpenUrl = onOpenUrl, modifier = Modifier.fillMaxWidth())
                        }
                        // Poll
                        if (message.contentType == ContentType.POLL) {
                            PollBubble(messageId = message.id, pollContent = message.content, isOwn = isOwn, modifier = Modifier.fillMaxWidth())
                        }
                        // GIF
                        if (message.contentType == ContentType.GIF && message.mediaUrl != null) {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = stringResource(Res.string.attach_gif),
                                modifier = Modifier.fillMaxWidth().heightIn(max = MuhabbetSizes.ImagePreviewMaxHeight)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { message.mediaUrl?.let { onImageClick(it) } },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                        }
                        // Sticker
                        if (message.contentType == ContentType.STICKER && message.mediaUrl != null) {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = stringResource(Res.string.attach_sticker),
                                modifier = Modifier.size(MuhabbetSizes.StickerSize).padding(MuhabbetSpacing.XSmall)
                                    .clickable { message.mediaUrl?.let { onImageClick(it) } },
                                contentScale = ContentScale.Fit
                            )
                        }
                        // Image
                        if (message.contentType == ContentType.IMAGE && (message.mediaUrl != null || message.thumbnailUrl != null)) {
                            AsyncImage(
                                model = message.thumbnailUrl ?: message.mediaUrl,
                                contentDescription = stringResource(Res.string.chat_photo),
                                modifier = Modifier.fillMaxWidth().heightIn(max = MuhabbetSizes.ImagePreviewMaxHeight)
                                    .clip(MaterialTheme.shapes.medium)
                                    // The bubble draws whenever EITHER url is present, so the tap has to
                                    // accept the same pair or a thumbnail-only image renders perfectly and
                                    // is dead to touch. The viewer is a plain zoomable AsyncImage, so a
                                    // thumbnail opens fine, just soft: 320px is all the client was given
                                    // for that message and there is no id to ask the server for more.
                                    .clickable { (message.mediaUrl ?: message.thumbnailUrl)?.let(onImageClick) },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                        }
                        // Video
                        if (message.contentType == ContentType.VIDEO && (message.mediaUrl != null || message.thumbnailUrl != null)) {
                            Box(
                                modifier = Modifier.fillMaxWidth().heightIn(max = MuhabbetSizes.ImagePreviewMaxHeight)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { message.mediaUrl?.let { onImageClick(it) } },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = message.thumbnailUrl ?: message.mediaUrl,
                                    contentDescription = stringResource(Res.string.video_play),
                                    modifier = Modifier.fillMaxWidth().heightIn(max = MuhabbetSizes.ImagePreviewMaxHeight),
                                    contentScale = ContentScale.Crop
                                )
                                // Play button overlay
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                    modifier = Modifier.size(MuhabbetSizes.MediaControl)
                                ) {
                                    Icon(
                                        Muhabbet.icons.Play,
                                        contentDescription = stringResource(Res.string.video_play),
                                        modifier = Modifier.padding(MuhabbetSpacing.Small),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                        }
                        // Text
                        if (message.contentType == ContentType.TEXT ||
                            (message.contentType == ContentType.IMAGE && message.content != stringResource(Res.string.chat_photo) && message.content.isNotBlank())
                        ) {
                            Text(
                                text = message.content,
                                style = Muhabbet.text.ChatBody,
                                color = onBubbleColor,
                                modifier = Modifier.padding(horizontal = MuhabbetSpacing.Small)
                            )
                            if (message.contentType == ContentType.TEXT) {
                                val firstUrl = extractFirstUrl(message.content)
                                if (firstUrl != null) {
                                    LinkPreviewCard(url = firstUrl, isOwn = isOwn, onOpenUrl = onOpenUrl)
                                }
                            }
                            Spacer(Modifier.height(MuhabbetSizes.GapHairline))
                        }
                    }

                    // Timestamp + edited + delivery status
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSizes.GapHairline),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MuhabbetSizes.QuoteBarWidth)
                    ) {
                        if (message.editedAt != null && !message.isDeleted) {
                            Text(
                                text = stringResource(Res.string.chat_edited),
                                style = Muhabbet.text.ChatMeta,
                                color = onBubbleColor.copy(alpha = 0.5f)
                            )
                        }
                        val timestamp = message.serverTimestamp ?: message.clientTimestamp
                        Text(
                            text = formatMessageTime(timestamp),
                            style = Muhabbet.text.ChatMeta,
                            color = onBubbleColor.copy(alpha = 0.6f)
                        )
                        if (isOwn && !message.isDeleted) {
                            val (icon, tint) = when (message.status) {
                                MessageStatus.SENDING -> Muhabbet.icons.Pending to onBubbleColor.copy(alpha = 0.5f)
                                MessageStatus.SENT -> Muhabbet.icons.Sent to onBubbleColor.copy(alpha = 0.7f)
                                MessageStatus.DELIVERED -> Muhabbet.icons.Delivered to onBubbleColor.copy(alpha = 0.7f)
                                MessageStatus.READ -> Muhabbet.icons.Delivered to semanticColors.statusRead
                            }
                            val statusDesc = when (message.status) {
                                MessageStatus.SENDING -> stringResource(Res.string.status_sending)
                                MessageStatus.SENT -> stringResource(Res.string.status_sent)
                                MessageStatus.DELIVERED -> stringResource(Res.string.status_delivered)
                                MessageStatus.READ -> stringResource(Res.string.status_read)
                            }
                            // The tick changes because the other device answered, so it is worth
                            // showing rather than swapping. Effects spring, not spatial: this is a
                            // colour and glyph change, and a tick that overshoots reads as a twitch.
                            // The label sits on the wrapper so a screen reader is not re-announced
                            // mid-crossfade.
                            AnimatedContent(
                                targetState = message.status,
                                transitionSpec = {
                                    fadeIn(Muhabbet.motion.effectsDefault()) togetherWith
                                        fadeOut(Muhabbet.motion.effectsFast())
                                },
                                label = "deliveryStatus",
                                modifier = Modifier.semantics { contentDescription = statusDesc }
                            ) { _ ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(MuhabbetSizes.IconStatusTick),
                                    tint = tint
                                )
                            }
                        }
                    }

                    // Reaction badges
                    if (message.reactions.isNotEmpty()) {
                        ReactionBadges(
                            reactions = message.reactions,
                            currentUserReactions = message.myReactions,
                            onReactionClick = onReactionToggle,
                            modifier = Modifier.padding(horizontal = MuhabbetSpacing.XSmall)
                        )
                    }
                }
            }

            // Context menu
            DropdownMenu(expanded = showContextMenu, onDismissRequest = onDismissMenu) {
                // Copy — the most fundamental message action
                if (message.contentType == ContentType.TEXT && !message.isDeleted) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.chat_context_copy)) },
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            onCopy()
                        },
                        leadingIcon = { Icon(Muhabbet.icons.Copy, contentDescription = stringResource(Res.string.chat_context_copy), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.chat_context_reply)) },
                    onClick = onReply,
                    leadingIcon = { Icon(Muhabbet.icons.Reply, contentDescription = stringResource(Res.string.chat_context_reply), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.chat_context_forward)) },
                    onClick = onForward,
                    leadingIcon = { Icon(Muhabbet.icons.Send, contentDescription = stringResource(Res.string.chat_context_forward), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                )
                DropdownMenuItem(
                    text = { Text(if (isStarred) stringResource(Res.string.chat_context_unstar) else stringResource(Res.string.chat_context_star)) },
                    onClick = onStar,
                    leadingIcon = {
                        Icon(
                            if (isStarred) Muhabbet.icons.Star else Muhabbet.icons.StarOutline,
                            contentDescription = null, modifier = Modifier.size(MuhabbetSizes.IconMedium),
                            tint = if (isStarred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.chat_context_info)) },
                    onClick = onInfo,
                    leadingIcon = { Icon(Muhabbet.icons.Info, contentDescription = stringResource(Res.string.chat_context_info), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                )
                if (isOwn) {
                    if (message.contentType == ContentType.TEXT) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.chat_context_edit)) },
                            onClick = onEdit,
                            leadingIcon = { Icon(Muhabbet.icons.Edit, contentDescription = stringResource(Res.string.chat_context_edit), modifier = Modifier.size(MuhabbetSizes.IconMedium)) }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.chat_context_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = onDelete,
                        leadingIcon = { Icon(Muhabbet.icons.Delete, contentDescription = stringResource(Res.string.chat_context_delete), modifier = Modifier.size(MuhabbetSizes.IconMedium), tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

internal fun formatMessageTime(instant: kotlinx.datetime.Instant): String =
    DateTimeFormatter.formatTime(instant)

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
