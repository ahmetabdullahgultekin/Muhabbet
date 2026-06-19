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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.muhabbet.app.platform.AudioPlayer
import com.muhabbet.app.ui.theme.LocalSemanticColors
import com.muhabbet.app.ui.theme.MuhabbetElevation
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

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
    val onViewOnce: (String) -> Unit
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

    Box(modifier = modifier.fillMaxWidth().background(LocalSemanticColors.current.chatWallpaper)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = MuhabbetSpacing.Medium, vertical = MuhabbetSpacing.Small),
            verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)
        ) {
            if (isLoadingMore) item(key = "loading_more") {
                Box(Modifier.fillMaxWidth().padding(MuhabbetSpacing.Small), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
                    var swipeOffset by remember { mutableStateOf(0f) }

                    Box(modifier = Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = { if (swipeOffset > 80f && !message.isDeleted) actions.onSwipeReply(message); swipeOffset = 0f },
                            onDragCancel = { swipeOffset = 0f },
                            onHorizontalDrag = { _, d -> swipeOffset = (swipeOffset + d).coerceIn(0f, 120f) }
                        )
                    }) {
                        if (swipeOffset > 20f) Box(Modifier.align(Alignment.CenterStart).padding(start = MuhabbetSpacing.XSmall), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = (swipeOffset / 80f).coerceIn(0f, 1f)))
                        }
                        Column(modifier = Modifier.padding(start = (swipeOffset / 3f).coerceAtMost(30f).dp)) {
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
                                onViewOnce = { id -> actions.onViewOnce(id) }
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
                modifier = Modifier.align(Alignment.BottomEnd).padding(MuhabbetSpacing.Large).size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.KeyboardArrowDown, stringResource(Res.string.scroll_to_bottom), Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
