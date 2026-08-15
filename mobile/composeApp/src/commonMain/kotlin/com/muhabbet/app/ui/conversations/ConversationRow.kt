package com.muhabbet.app.ui.conversations

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.a11y_conversation_pinned
import com.muhabbet.composeapp.generated.resources.cd_group_avatar
import com.muhabbet.shared.dto.ConversationResponse
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.Muhabbet

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConversationItem(
    conversation: ConversationResponse,
    displayName: String,
    avatarUrl: String? = null,
    isOnline: Boolean,
    isGroup: Boolean = false,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPin: () -> Unit = {}
) {
    val hasUnread = conversation.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MuhabbetSizes.ChatListItemMinHeight)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with online indicator
        Box {
            UserAvatar(
                avatarUrl = avatarUrl,
                displayName = displayName,
                size = MuhabbetSizes.AvatarChatList,
                isGroup = isGroup,
                contentDescription = if (isGroup) stringResource(Res.string.cd_group_avatar) else displayName
            )
            // Green online dot
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.dp, y = 1.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(LocalSemanticColors.current.statusOnline, CircleShape)
                )
            }
        }

        Spacer(Modifier.width(MuhabbetSpacing.Large))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    style = Muhabbet.text.ConversationTitle,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPinned) {
                    Spacer(Modifier.width(MuhabbetSpacing.XSmall))
                    // "Pinned" is state carried by this icon alone — nothing in the row's text
                    // repeats it, so a null description would silently drop it.
                    Icon(
                        Muhabbet.icons.Pin,
                        contentDescription = stringResource(Res.string.a11y_conversation_pinned),
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val preview = conversation.lastMessagePreview
            if (preview != null) {
                Text(
                    text = preview,
                    style = Muhabbet.text.ConversationPreview,
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (hasUnread) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            val lastAt = conversation.lastMessageAt
            if (lastAt != null) {
                Text(
                    text = formatTimestamp(lastAt),
                    style = Muhabbet.text.ConversationTimestamp,
                    color = if (hasUnread) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasUnread) {
                Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                Badge(
                    containerColor = LocalSemanticColors.current.unreadBadge,
                    contentColor = LocalSemanticColors.current.onUnreadBadge
                ) {
                    Text(conversation.unreadCount.toString())
                }
            }
        }
    }
}

@Composable
internal fun ConversationSkeletonItem() {
    val shimmerAlpha = shimmerAlpha()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(MuhabbetSizes.AvatarMedium)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha))
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            // Name placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(14.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha))
            )
            Spacer(Modifier.height(MuhabbetSpacing.Small))
            // Message preview placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha))
            )
        }
        Spacer(Modifier.width(MuhabbetSpacing.Small))
        // Timestamp placeholder
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(10.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = shimmerAlpha))
        )
    }
}

@Composable
private fun shimmerAlpha(): Float {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "shimmerTransition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = com.muhabbet.designsystem.theme.MuhabbetDurations.ShimmerDurationMs,
                easing = androidx.compose.animation.core.LinearEasing
            ),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "shimmer"
    )
    return alpha
}

internal fun formatTimestamp(timestamp: String): String =
    DateTimeFormatter.formatConversationTimestamp(timestamp)
