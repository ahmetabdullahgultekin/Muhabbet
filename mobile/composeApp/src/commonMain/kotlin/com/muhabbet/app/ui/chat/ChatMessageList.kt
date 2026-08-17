package com.muhabbet.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muhabbet.app.data.repository.WallpaperRepository
import com.muhabbet.app.platform.AudioPlayer
import com.muhabbet.app.util.hexToColorOrNull
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.LocalThemeMode
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.ResolvedThemeMode
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D

/**
 * Per-message action callbacks for [ChatMessageList]. Grouped into a holder to keep the
 * list composable's parameter list readable.
 */
internal class ChatMessageActions(
    val onSwipeReply: (Message) -> Unit,
    val onLongPress: (Message) -> Unit,
    val onDismissMenu: () -> Unit,
    val onReply: (Message) -> Unit,
    val onForward: (Message) -> Unit,
    val onStar: (Message, Boolean) -> Unit,
    val onEdit: (Message) -> Unit,
    val onDelete: (Message) -> Unit,
    val onImageClick: (String) -> Unit,
    val onReactionToggle: (Message, String) -> Unit,
    val onQuickReaction: (Message, String) -> Unit,
    val onInfo: (Message) -> Unit,
    val onViewOnce: (String) -> Unit,
    /**
     * Documents, link previews, shared locations, videos and URLs inside message text; the screen
     * owns opening and its failure.
     */
    val onOpenUrl: (String) -> Unit,
    /** A video bubble drawn from a thumbnail whose video itself is not reachable (#361). */
    val onMediaUnavailable: () -> Unit
)

/**
 * The scrolling message list (date separators, swipe-to-reply, bubbles, typing indicator)
 * plus the floating scroll-to-bottom button. Extracted from `ChatScreen` for SRP.
 */
@Composable
internal fun ChatMessageList(
    messages: List<Message>,
    currentUserId: String,
    starredIds: Set<String>,
    audioPlayer: AudioPlayer,
    isLoadingMore: Boolean,
    peerTyping: Boolean,
    contextMenuMessageId: String?,
    listState: LazyListState,
    scope: CoroutineScope,
    actions: ChatMessageActions,
    modifier: Modifier = Modifier
) {
    var reactionTargetId by remember { mutableStateOf<String?>(null) }
    val showScrollToBottom = remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.isNotEmpty() && last < messages.lastIndex - 2
        }
    }

    // The reader half of #380: WallpaperPickerScreen persists a selection but nothing used to
    // consult it here, so the chat always drew the theme default no matter what was picked.
    // Re-read on every fresh composition of this screen, same as the picker itself does on open.
    val wallpaperRepository: WallpaperRepository = koinInject()
    val isDarkTheme = LocalThemeMode.current != ResolvedThemeMode.Light
    val wallpaper = remember(isDarkTheme) { wallpaperRepository.resolveWallpaper(isDarkTheme) }
    val defaultWallpaperColor = LocalSemanticColors.current.chatWallpaper.container

    Box(modifier = modifier.fillMaxWidth()) {
        when (wallpaper) {
            is WallpaperRepository.ChatWallpaper.Custom -> AsyncImage(
                model = "file://${wallpaper.path}",
                // Decorative background, not content — a screen reader has nothing useful to
                // announce about a chat's wallpaper.
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                // If the file was removed from under the app (cleared storage, restored backup),
                // fall back to the same default the DEFAULT branch below paints, rather than a
                // blank or broken image.
                error = ColorPainter(defaultWallpaperColor)
            )
            is WallpaperRepository.ChatWallpaper.Solid -> Box(
                modifier = Modifier.fillMaxSize()
                    .background(wallpaper.hexColor.hexToColorOrNull() ?: defaultWallpaperColor)
            )
            WallpaperRepository.ChatWallpaper.Default -> Box(
                modifier = Modifier.fillMaxSize().background(defaultWallpaperColor)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = MuhabbetSpacing.Medium, vertical = MuhabbetSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)
        ) {
            if (isLoadingMore) item(key = "loading_more") {
                Box(Modifier.fillMaxWidth().padding(MuhabbetSpacing.Small), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(MuhabbetSizes.IconLarge))
                }
            }
            var lastDateStr = ""
            messages.forEachIndexed { index, message ->
                val dateStr = formatDateForSeparator(message.serverTimestamp ?: message.clientTimestamp)
                if (dateStr != lastDateStr) { lastDateStr = dateStr; val d = dateStr; item(key = "date_$index") { DateSeparatorPill(d) } }
                item(key = message.id) {
                    val isOwn = message.senderId == currentUserId
                    val repliedMessage = message.replyToId?.let { rid -> messages.firstOrNull { it.id == rid } }
                    val isStarred = message.id in starredIds
                    val threshold = Muhabbet.gestures.SwipeReplyThreshold
                    val maxSwipe = Muhabbet.gestures.SwipeReplyMax
                    // Animatable, not a plain Float: releasing a drag below the threshold used to
                    // snap the bubble back to zero in one frame. It now springs back, which is what
                    // the gesture promised while the finger was down.
                    val swipeOffset = remember(maxSwipe) { swipeReplyOffset(maxSwipe) }
                    var isArmed by remember { mutableStateOf(false) }
                    val haptics = Muhabbet.haptics
                    val scope = rememberCoroutineScope()

                    Box(modifier = Modifier.pointerInput(message.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffset.value > threshold && !message.isDeleted) {
                                    haptics.perform(MuhabbetHapticIntent.SwipeCommitted)
                                    actions.onSwipeReply(message)
                                }
                                isArmed = false
                                scope.launch { swipeOffset.animateTo(0f, Muhabbet.motion.spatialDefault()) }
                            },
                            onDragCancel = {
                                isArmed = false
                                scope.launch { swipeOffset.animateTo(0f, Muhabbet.motion.spatialDefault()) }
                            },
                            onHorizontalDrag = { _, d ->
                                val next = (swipeOffset.value + d).coerceIn(0f, maxSwipe)
                                // Fired once on the way past the threshold, not on every frame past
                                // it: a haptic per drag event is a buzz, not a signal.
                                if (next > threshold && !isArmed) {
                                    isArmed = true
                                    haptics.perform(MuhabbetHapticIntent.SwipeArmed)
                                } else if (next <= threshold) {
                                    isArmed = false
                                }
                                scope.launch { swipeOffset.snapTo(next) }
                            }
                        )
                    }) {
                        if (swipeOffset.value > SwipeHintVisibleAt) Box(Modifier.align(Alignment.CenterStart).padding(start = MuhabbetSpacing.XSmall), contentAlignment = Alignment.Center) {
                            // Decorative: this arrow only fades in mid-drag as a swipe-to-reply hint.
                            // A screen-reader user never performs the drag — they reach Reply through
                            // the long-press context menu, which is labelled — so naming it here would
                            // announce a control that is not reachable that way.
                            Icon(
                                imageVector = Muhabbet.icons.Reply,
                                contentDescription = null,
                                modifier = Modifier.size(MuhabbetSizes.IconMedium),
                                tint = MaterialTheme.colorScheme.primary.copy(
                                    alpha = (swipeOffset.value / threshold).coerceIn(0f, 1f)
                                )
                            )
                        }
                        Column(modifier = Modifier.padding(start = (swipeOffset.value / SwipeTravelDivisor).coerceAtMost(MaxSwipeShiftPx).dp)) {
                            if (reactionTargetId == message.id) QuickReactionBar(visible = true, onReaction = { emoji -> reactionTargetId = null; actions.onQuickReaction(message, emoji) })
                            MessageBubble(message, isOwn, audioPlayer, repliedMessage, isStarred,
                                showContextMenu = contextMenuMessageId == message.id,
                                onLongPress = { if (!message.isDeleted) actions.onLongPress(message) },
                                onDoubleTap = { if (!message.isDeleted) reactionTargetId = if (reactionTargetId == message.id) null else message.id },
                                onDismissMenu = { actions.onDismissMenu() },
                                onReply = { actions.onReply(message) },
                                onForward = { actions.onForward(message) },
                                onStar = { actions.onStar(message, isStarred) },
                                onEdit = { actions.onEdit(message) },
                                onDelete = { actions.onDelete(message) },
                                onImageClick = { actions.onImageClick(it) },
                                onReactionToggle = { emoji -> actions.onReactionToggle(message, emoji) },
                                onInfo = { actions.onInfo(message) },
                                onViewOnce = { id -> actions.onViewOnce(id) },
                                onOpenUrl = { url -> actions.onOpenUrl(url) },
                                onMediaUnavailable = { actions.onMediaUnavailable() }
                            )
                        }
                    }
                }
            }
            if (peerTyping) item(key = "typing") { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { TypingIndicatorBubble() } }
        }
        if (showScrollToBottom.value) {
            Surface(
                onClick = { scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) } },
                shape = CircleShape,
                shadowElevation = MuhabbetElevation.Level5,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(MuhabbetSpacing.Large).size(MuhabbetSizes.MinTouchTarget)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Muhabbet.icons.ScrollDown, stringResource(Res.string.scroll_to_bottom), Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/**
 * The swipe-to-reply drag distance in pixels, as an [Animatable] that is bounded to its own domain.
 *
 * The bounds are the whole point. This value means "how far the finger has dragged this bubble
 * toward Reply", so `0..max` is not a safety margin, it is the definition — and `onHorizontalDrag`
 * has always coerced into it. The spring-back added in 0.3.0 did not carry that invariant over:
 * `MuhabbetMotion.spatialDefault()` is under-damped **on purpose** (damping 0.80, documented there
 * as the thing that "reads as physical rather than scripted"), so animating to `0f` crosses zero and
 * settles from below. Roughly 270 ms and sixteen frames of the settle are negative.
 *
 * That negative reached `Modifier.padding(start = …)`, whose element constructor requires a
 * non-negative `Dp`, and release build 0.3.0 died with `IllegalArgumentException: Padding must be
 * non-negative` the first time anyone released a swipe-to-reply. Before 0.3.0 `swipeOffset` was a
 * plain `Float` that only ever moved by `coerceIn(0f, max)` or an assignment to `0f`, so the same
 * padding expression could not go negative and the `coerceAtMost` upper cap was sufficient.
 *
 * Declaring the domain on the state holder fixes it once for all three readers — hint visibility,
 * arrow alpha and the bubble shift — instead of each of them defending itself, which would leave the
 * next reader to rediscover this. Compose ends the animation at the bound, so the spring-back still
 * reads as a spring; the only motion lost is a sub-dp bounce past rest that a start padding could
 * never have rendered in the first place.
 */
internal fun swipeReplyOffset(max: Float): Animatable<Float, AnimationVector1D> =
    Animatable(0f).apply { updateBounds(lowerBound = 0f, upperBound = max) }

/** How far the finger must travel before the reply arrow starts fading in. */
private const val SwipeHintVisibleAt = 20f

/** The bubble follows the finger at a third of its speed, so the drag feels weighted. */
private const val SwipeTravelDivisor = 3f

/** Cap on how far the bubble itself shifts, regardless of how far the finger goes. */
private const val MaxSwipeShiftPx = 30f
