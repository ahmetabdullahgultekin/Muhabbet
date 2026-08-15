package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.UserStatusGroup
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.Message
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetChip

internal enum class ConversationFilter {
    ALL, UNREAD, FAVORITES, GROUPS
}

/**
 * Flat list of message-search hits. Tapping a hit opens its conversation.
 */
@Composable
internal fun MessageSearchResults(
    results: List<Message>,
    conversations: List<ConversationResponse>,
    currentUserId: String,
    modifier: Modifier = Modifier,
    onResultClick: (ChatTarget) -> Unit
) {
    LazyColumn(modifier = modifier) {
        items(results, key = { it.id }) { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val conv = conversations.firstOrNull { it.id == msg.conversationId }
                        val otherP = conv?.participants?.firstOrNull { it.userId != currentUserId }
                        val name = conv?.name ?: otherP?.displayName ?: otherP?.phoneNumber ?: ""
                        val isGroup = conv?.type == ConversationType.GROUP
                        onResultClick(
                            ChatTarget(
                                conversationId = msg.conversationId,
                                name = name,
                                otherUserId = otherP?.userId,
                                isGroup = isGroup,
                                avatarUrl = if (isGroup) conv?.avatarUrl else otherP?.avatarUrl
                            )
                        )
                    }
                    .padding(horizontal = MuhabbetSpacing.Large, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = msg.content.take(80),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                    Text(
                        text = formatTimestamp(msg.serverTimestamp?.toString() ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

/**
 * Horizontal "stories" row: an add-status button followed by contact status avatars.
 */
@Composable
internal fun ConversationStatusRow(
    statusGroups: List<UserStatusGroup>,
    conversations: List<ConversationResponse>,
    onAddStatus: () -> Unit,
    onStatusClick: (userId: String, displayName: String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = MuhabbetSpacing.Small),
        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium),
        contentPadding = PaddingValues(horizontal = MuhabbetSpacing.Medium)
    ) {
        item(key = "add_status") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onAddStatus() }.width(64.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Muhabbet.icons.Add,
                            contentDescription = stringResource(Res.string.status_create_title),
                            modifier = Modifier.size(MuhabbetSizes.IconLarge),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                Text(
                    text = stringResource(Res.string.status_my),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        items(
            count = statusGroups.size,
            key = { statusGroups[it].userId }
        ) { index ->
            val group = statusGroups[index]
            val participant = conversations.flatMap { it.participants }
                .firstOrNull { it.userId == group.userId }
            val displayName = participant?.displayName ?: participant?.phoneNumber ?: group.userId.take(8)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(64.dp).clickable { onStatusClick(group.userId, displayName) }
            ) {
                Surface(
                    shape = CircleShape,
                    modifier = Modifier
                        .size(56.dp)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    UserAvatar(
                        avatarUrl = participant?.avatarUrl,
                        displayName = displayName,
                        size = MuhabbetSizes.AvatarLarge
                    )
                }
                Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    if (statusGroups.isNotEmpty()) {
        HorizontalDivider()
    }
}

/**
 * Row of conversation filter chips (All / Unread / Favorites / Groups).
 */
@Composable
internal fun ConversationFilterChips(
    activeFilter: ConversationFilter,
    onFilterChange: (ConversationFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = MuhabbetSpacing.XSmall),
        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small),
        contentPadding = PaddingValues(horizontal = MuhabbetSpacing.Medium)
    ) {
        item {
            MuhabbetChip(
                label = stringResource(Res.string.filter_all),
                selected = activeFilter == ConversationFilter.ALL,
                onClick = { onFilterChange(ConversationFilter.ALL) }
            )
        }
        item {
            MuhabbetChip(
                label = stringResource(Res.string.filter_unread),
                selected = activeFilter == ConversationFilter.UNREAD,
                onClick = { onFilterChange(if (activeFilter == ConversationFilter.UNREAD) ConversationFilter.ALL else ConversationFilter.UNREAD) }
            )
        }
        item {
            MuhabbetChip(
                label = stringResource(Res.string.filter_favorites),
                selected = activeFilter == ConversationFilter.FAVORITES,
                onClick = { onFilterChange(if (activeFilter == ConversationFilter.FAVORITES) ConversationFilter.ALL else ConversationFilter.FAVORITES) }
            )
        }
        item {
            MuhabbetChip(
                label = stringResource(Res.string.filter_groups),
                selected = activeFilter == ConversationFilter.GROUPS,
                onClick = { onFilterChange(if (activeFilter == ConversationFilter.GROUPS) ConversationFilter.ALL else ConversationFilter.GROUPS) }
            )
        }
    }
}
