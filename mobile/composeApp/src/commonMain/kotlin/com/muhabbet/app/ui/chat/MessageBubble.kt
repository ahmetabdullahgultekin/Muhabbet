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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.muhabbet.app.platform.AudioPlayer
import com.muhabbet.app.util.findUrlSpans
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.designsystem.components.MuhabbetMenu
import com.muhabbet.designsystem.components.MuhabbetMenuItem
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
import kotlin.time.Clock
import com.muhabbet.composeapp.generated.resources.chat_context_edit_expired
import com.muhabbet.shared.validation.ValidationRules

/** Opacity of the disc the play glyph sits on, so it stays legible over any thumbnail. */
private const val PlayOverlayAlpha = 0.7f

/**
 * How far a bubble's timestamp falls back from its body text.
 *
 * Safe only because it is drawn on an **opaque** bubble: the composite is the bubble's own two
 * colours and nothing else can reach it. The same 0.6 over a translucent ground is what #678 was.
 */
private const val MetaTextAlpha = 0.6f

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
    // Documents, link previews, shared locations, videos and URLs inside message text all end in
    // "hand this URL to the platform". The bubble does not do it itself: opening can fail (nothing
    // installed that handles the URL), and the snackbar that has to say so lives on the screen.
    onOpenUrl: (String) -> Unit = {},
    // A video that arrived as a thumbnail with no playable url. Same division of labour: the bubble
    // knows the media is unreachable, the screen owns telling the user.
    onMediaUnavailable: () -> Unit = {}
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
    // A deleted message is a tombstone rather than a message, so it takes its own ground — and it
    // takes the foreground that ground ships with. Both halves opaque: the previous treatment drew
    // a 50%-alpha bubble and kept the OPAQUE bubble's text colour on it, so the chat wallpaper bled
    // through into the ground its own label was read against (#678).
    val bubble = when {
        message.isDeleted -> semanticColors.bubbleDeleted
        isOwn -> semanticColors.bubbleOwn
        else -> semanticColors.bubbleOther
    }
    val bubbleColor = bubble.container
    val onBubbleColor = bubble.content
    // The tombstone's content colour is already the quiet one, chosen and measured as such. Taking
    // another 40% off it for the timestamp would put it back under the floor the rest of this change
    // exists to clear, so a deleted bubble carries no alpha anywhere.
    val metaColor = if (message.isDeleted) onBubbleColor else onBubbleColor.copy(alpha = MetaTextAlpha)
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
                    // Quoted reply. Not on a deleted message: the tombstone is one line, and the
                    // quote panel is the last translucent chrome that would otherwise be drawn on
                    // it — a ground inside a ground, each at its own alpha, measurable by nobody.
                    if (repliedMessage != null && !message.isDeleted) {
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

                    // Forwarded label. Same reason as the quote above, plus: a deleted message has
                    // no content left to have been forwarded, so the caption describes nothing.
                    if (message.forwardedFrom != null && !message.isDeleted) {
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
                        // Three channels say "deleted", none of them opacity: the bubble's own
                        // muted ground, an italic label, and the circle-slash. The glyph carries no
                        // contentDescription because the sentence beside it says the same thing —
                        // a screen reader announcing "blocked, this message was deleted" is worse
                        // than one announcing the sentence alone.
                        Row(
                            modifier = Modifier.padding(horizontal = MuhabbetSpacing.Small, vertical = MuhabbetSpacing.XSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)
                        ) {
                            Icon(
                                Muhabbet.icons.MessageDeleted,
                                contentDescription = null,
                                modifier = Modifier.size(MuhabbetSizes.IconSmall),
                                tint = onBubbleColor
                            )
                            Text(
                                text = stringResource(Res.string.chat_message_deleted),
                                style = Muhabbet.text.ChatDeletedLabel,
                                color = onBubbleColor
                            )
                        }
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
                            // #357 gave images `(mediaUrl ?: thumbnailUrl)` because for an image the
                            // two urls are the same picture at two resolutions. For a video they are
                            // not: the thumbnail is a still frame. So the pair is not interchangeable
                            // here, and only mediaUrl can be played.
                            //
                            // Playback goes to the platform via onOpenUrl, not to onImageClick: that
                            // opens MediaViewer, a zoomable AsyncImage which cannot decode a video and
                            // would show an empty frame that looks like a broken player. There is no
                            // in-app player in this app, and adding one is not a bubble's change.
                            val playableUrl = message.mediaUrl
                            Box(
                                modifier = Modifier.fillMaxWidth().heightIn(max = MuhabbetSizes.ImagePreviewMaxHeight)
                                    .clip(MaterialTheme.shapes.medium)
                                    // The bubble draws whenever EITHER url is present, so every draw
                                    // has to answer a tap. Thumbnail-only means the video itself is
                                    // not reachable, and opening the still frame instead would look
                                    // like playback and be a lie, so it says so.
                                    .clickable {
                                        if (playableUrl != null) onOpenUrl(playableUrl) else onMediaUnavailable()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = message.thumbnailUrl ?: message.mediaUrl,
                                    contentDescription = stringResource(Res.string.chat_video),
                                    modifier = Modifier.fillMaxWidth().heightIn(max = MuhabbetSizes.ImagePreviewMaxHeight),
                                    contentScale = ContentScale.Crop
                                )
                                // Play button overlay — drawn only when there is something to play,
                                // because the affordance is the promise.
                                if (playableUrl != null) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = PlayOverlayAlpha),
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
                            }
                            Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                        }
                        // Text
                        if (message.contentType == ContentType.TEXT ||
                            (message.contentType == ContentType.IMAGE && message.content != stringResource(Res.string.chat_photo) && message.content.isNotBlank())
                        ) {
                            Text(
                                text = linkifiedContent(message.content, semanticColors.linkColor, onOpenUrl),
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
                            color = metaColor
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
            MuhabbetMenu(expanded = showContextMenu, onDismissRequest = onDismissMenu) {
                // Copy — the most fundamental message action
                if (message.contentType == ContentType.TEXT && !message.isDeleted) {
                    MuhabbetMenuItem(
                        text = stringResource(Res.string.chat_context_copy),
                        icon = Muhabbet.icons.Copy,
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            onCopy()
                        }
                    )
                }
                MuhabbetMenuItem(
                    text = stringResource(Res.string.chat_context_reply),
                    icon = Muhabbet.icons.Reply,
                    onClick = onReply
                )
                MuhabbetMenuItem(
                    text = stringResource(Res.string.chat_context_forward),
                    icon = Muhabbet.icons.Send,
                    onClick = onForward
                )
                MuhabbetMenuItem(
                    text = if (isStarred) stringResource(Res.string.chat_context_unstar) else stringResource(Res.string.chat_context_star),
                    icon = if (isStarred) Muhabbet.icons.Star else Muhabbet.icons.StarOutline,
                    iconTint = if (isStarred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                    onClick = onStar
                )
                MuhabbetMenuItem(
                    text = stringResource(Res.string.chat_context_info),
                    icon = Muhabbet.icons.Info,
                    onClick = onInfo
                )
                if (isOwn) {
                    if (message.contentType == ContentType.TEXT) {
                        // The server has always refused an edit after fifteen minutes; the app used
                        // to offer it anyway, let the user retype the message, and only then fail
                        // with "mesaj gönderilemedi" — wrong twice over, since nothing was being
                        // sent and that was not the reason (#597).
                        //
                        // Disabled with the reason in the label, not hidden: a menu item that
                        // disappears reads as broken, where a greyed one teaches the rule. And the
                        // window comes from ValidationRules, the same constant the server checks,
                        // so the two cannot drift apart.
                        //
                        // A message with no serverTimestamp has not been acknowledged yet — it is
                        // still in flight — so it counts as editable rather than expired.
                        val editable = message.serverTimestamp?.let { sentAt ->
                            ValidationRules.isWithinEditWindow(
                                sentAtEpochMillis = sentAt.toEpochMilliseconds(),
                                nowEpochMillis = Clock.System.now().toEpochMilliseconds()
                            )
                        } ?: true

                        MuhabbetMenuItem(
                            text = if (editable) stringResource(Res.string.chat_context_edit)
                            else stringResource(Res.string.chat_context_edit_expired),
                            icon = Muhabbet.icons.Edit,
                            enabled = editable,
                            onClick = onEdit
                        )
                    }
                    MuhabbetMenuItem(
                        text = stringResource(Res.string.chat_context_delete),
                        icon = Muhabbet.icons.Delete,
                        destructive = true,
                        onClick = onDelete
                    )
                }
            }
        }
    }
}

/**
 * The message text, with every URL in it turned into a tappable, underlined link (#362).
 *
 * Before this, only the link *preview card* was tappable. The card is decorative enrichment that
 * renders nothing when the fetch fails or the site serves no metadata, so a message whose only
 * content was a URL could be completely unreachable — the address was on screen and there was no
 * way to follow it.
 *
 * The link range comes from `findUrlSpans`, the same detector the preview card uses, so the two
 * cannot disagree about where the URL ends. Ranges are annotated rather than the text rebuilt: the
 * user's own characters are displayed untouched, and a Turkish suffix like `'a` in
 * *"https://site.com'a bak"* stays visible as prose outside the tappable range.
 *
 * [linkColor] is `MuhabbetSemanticColors.linkColor`, which `SemanticColorContrastTest` already pins
 * above the WCAG text floor against both bubble colours — it was defined for this and never used.
 */
private fun linkifiedContent(
    content: String,
    linkColor: Color,
    onOpenUrl: (String) -> Unit
): AnnotatedString {
    val spans = findUrlSpans(content)
    if (spans.isEmpty()) return AnnotatedString(content)

    val linkStyle = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    )
    return buildAnnotatedString {
        var lastEnd = 0
        spans.forEach { span ->
            append(content.substring(lastEnd, span.start))
            // Clickable rather than LinkAnnotation.Url: opening has to go through the screen's
            // openExternally, which reports a failure the platform's default handler swallows.
            withLink(LinkAnnotation.Clickable(span.url, linkStyle) { onOpenUrl(span.url) }) {
                append(content.substring(span.start, span.endExclusive))
            }
            lastEnd = span.endExclusive
        }
        append(content.substring(lastEnd))
    }
}

internal fun formatMessageTime(instant: kotlinx.datetime.Instant): String =
    DateTimeFormatter.formatTime(instant)
