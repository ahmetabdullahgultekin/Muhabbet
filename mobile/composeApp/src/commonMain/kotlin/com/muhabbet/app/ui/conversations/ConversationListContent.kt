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
import com.muhabbet.shared.model.Message
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetChip
import com.muhabbet.designsystem.components.SettingsNavRow
import com.muhabbet.app.ui.components.rememberRelativeDayLabels

internal enum class ConversationFilter {
    ALL, UNREAD, FAVORITES, GROUPS
}

/**
 * The persistent entry point into archived chats — pinned above every active conversation, unlike
 * the section it replaces (#612) which sat below all of them and rendered nothing at all whenever
 * nothing was archived. Shown only when [count] is positive: archiving is already discoverable from
 * every row's long-press menu, so this row's job is purely "where did it go", which has nothing to
 * say until something has actually been archived. The first archive makes it appear immediately —
 * that appearance *is* how the mechanism teaches itself.
 */
@Composable
internal fun ArchivedChatsRow(count: Int, onClick: () -> Unit) {
    SettingsNavRow(
        title = stringResource(Res.string.conv_archived_section),
        subtitle = pluralStringResource(Res.plurals.archived_row_count, count, count),
        icon = Muhabbet.icons.Archive,
        // Decorative: the title beside it already says "archived", so naming the icon too would
        // just put a noise word ahead of what the screen reader announces next.
        onClick = onClick
    )
}

/**
 * One message-search hit. Tapping it opens the conversation the message is in.
 *
 * Extracted from [MessageSearchResults] so the home shell can put the same rows inside its own
 * LazyColumn (#638) — a nested LazyColumn is not an option, and two hand-written versions of "what a
 * search hit looks like" is how the two search screens came to disagree in the first place.
 */
@Composable
internal fun MessageSearchResultRow(
    message: Message,
    conversations: List<ConversationResponse>,
    currentUserId: String,
    fallbackName: String,
    onClick: (ChatTarget) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // A hit whose conversation is not in the loaded list used to open a chat named `""`
                // — the same empty header as #543, reached from search instead of Starred Messages.
                val conv = conversations.firstOrNull { it.id == message.conversationId }
                onClick(
                    conv?.toChatTarget(currentUserId, fallbackName)
                        ?: ChatTarget(conversationId = message.conversationId, name = fallbackName)
                )
            }
            .padding(horizontal = MuhabbetSpacing.Large, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = message.content.take(MESSAGE_SNIPPET_CHARS),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            Text(
                text = formatTimestamp(message.serverTimestamp?.toString() ?: "", rememberRelativeDayLabels()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Enough of the message to recognise it, short enough that every hit fits on two lines. */
private const val MESSAGE_SNIPPET_CHARS = 80

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
    val defaultChatName = stringResource(Res.string.chat_default_name)
    LazyColumn(modifier = modifier) {
        items(results, key = { it.id }) { msg ->
            MessageSearchResultRow(
                message = msg,
                conversations = conversations,
                currentUserId = currentUserId,
                fallbackName = defaultChatName,
                onClick = onResultClick
            )
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
    val unknownPersonLabel = stringResource(Res.string.unknown_person)
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
            // Same order as the Updates tab, and the same prohibition: never the user id, whose
            // first eight characters read as a hex hash (#507).
            val displayName = participant?.displayName
                ?: participant?.phoneNumber
                ?: group.displayName
                ?: unknownPersonLabel
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
