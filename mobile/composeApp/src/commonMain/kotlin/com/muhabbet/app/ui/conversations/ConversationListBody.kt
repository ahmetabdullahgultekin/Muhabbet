package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.shared.dto.UserStatusGroup
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.designsystem.components.EmptyChatsIllustration
import com.muhabbet.designsystem.components.MuhabbetSkeletonList
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/*
 * The list itself, split out of ConversationListScreen when that file passed 750 lines — well
 * over the 500-line guideline. The screen keeps the state and the effects; this keeps the
 * rendering of what that state produces.
 */

/**
 * Loading / empty / list body for [ConversationListScreen]. Splits active vs archived,
 * sorts pinned-first, and renders the status row + filter chips above the list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ConversationListBody(
    isLoading: Boolean,
    isRefreshing: Boolean,
    conversations: List<ConversationResponse>,
    activeFilter: ConversationFilter,
    onFilterChange: (ConversationFilter) -> Unit,
    showStatusRow: Boolean,
    statusGroups: List<UserStatusGroup>,
    currentUserId: String,
    contactNameMap: Map<String, String>,
    onlineUsers: Map<String, Boolean>,
    defaultChatName: String,
    onRefresh: () -> Unit,
    onAddStatus: () -> Unit,
    onStatusClick: (userId: String, displayName: String) -> Unit,
    onConversationClick: (id: String, name: String, otherUserId: String?, isGroup: Boolean) -> Unit,
    onConversationLongClick: (ConversationResponse) -> Unit,
    onPin: (ConversationResponse) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading) {
            // MuhabbetSkeletonList, not a LazyColumn of hand-rolled rows: it wraps its rows in a
            // single ShimmerHost. The old version called ConversationSkeletonItem per row, and that
            // function created its own rememberInfiniteTransition — eight placeholder rows meant
            // eight independent infinite animations, each waking the frame clock on its own.
            MuhabbetSkeletonList(modifier = Modifier.fillMaxSize())
        } else if (conversations.isEmpty()) {
            EmptyChatsIllustration(
                title = stringResource(Res.string.empty_chats_title),
                subtitle = stringResource(Res.string.empty_chats_subtitle),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Filter conversations
            val filteredConversations = when (activeFilter) {
                ConversationFilter.UNREAD -> conversations.filter { it.unreadCount > 0 }
                ConversationFilter.FAVORITES -> conversations.filter { it.isPinned }
                ConversationFilter.GROUPS -> conversations.filter { it.type == ConversationType.GROUP }
                else -> conversations
            }
            // Split active vs archived
            val activeConversations = filteredConversations.filter { !it.isArchived }
            val archivedConversations = filteredConversations.filter { it.isArchived }

            // Sort: pinned first, then by lastMessageAt
            val sortedConversations = activeConversations.sortedWith(
                compareByDescending<ConversationResponse> { it.isPinned }
                    .thenByDescending { it.lastMessageAt ?: "" }
            )

            LazyColumn {
                if (showStatusRow) {
                    item(key = "status_row") {
                        ConversationStatusRow(
                            statusGroups = statusGroups,
                            conversations = conversations,
                            onAddStatus = onAddStatus,
                            onStatusClick = onStatusClick
                        )
                    }
                }
                item(key = "filter_chips") {
                    ConversationFilterChips(activeFilter = activeFilter, onFilterChange = onFilterChange)
                }
                // animateItem: a conversation that jumps to the top on a new message, or slides out
                // when archived, moves there instead of teleporting. The app had zero uses of this
                // anywhere; the keys it needs were already in place.
                items(sortedConversations, key = { it.id }) { conv ->
                    ConversationListItemRow(
                        modifier = Modifier.animateItem(),
                        conv = conv,
                        currentUserId = currentUserId,
                        contactNameMap = contactNameMap,
                        onlineUsers = onlineUsers,
                        defaultChatName = defaultChatName,
                        isPinned = conv.isPinned,
                        onConversationClick = onConversationClick,
                        onConversationLongClick = onConversationLongClick,
                        onPin = { onPin(conv) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = MuhabbetSizes.ChatListDividerInset)
                    )
                }

                // Archived section
                if (archivedConversations.isNotEmpty()) {
                    item(key = "archived_header") {
                        Spacer(Modifier.height(MuhabbetSpacing.Medium))
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MuhabbetSpacing.XLarge, vertical = MuhabbetSpacing.Medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.conv_archived_section),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(MuhabbetSpacing.Small))
                            Text(
                                text = "(${archivedConversations.size})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(archivedConversations, key = { "archived_${it.id}" }) { conv ->
                        ConversationListItemRow(
                            modifier = Modifier.animateItem(),
                            conv = conv,
                            currentUserId = currentUserId,
                            contactNameMap = contactNameMap,
                            onlineUsers = onlineUsers,
                            defaultChatName = defaultChatName,
                            isPinned = false,
                            onConversationClick = onConversationClick,
                            onConversationLongClick = onConversationLongClick,
                            onPin = {}
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = MuhabbetSizes.ChatListDividerInset)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Resolves the display name/avatar/online state for a single conversation, then renders a
 * [ConversationItem]. Name priority: contact-saved name > nickname > phone.
 */
@Composable
private fun ConversationListItemRow(
    conv: ConversationResponse,
    modifier: Modifier = Modifier,
    currentUserId: String,
    contactNameMap: Map<String, String>,
    onlineUsers: Map<String, Boolean>,
    defaultChatName: String,
    isPinned: Boolean,
    onConversationClick: (id: String, name: String, otherUserId: String?, isGroup: Boolean) -> Unit,
    onConversationLongClick: (ConversationResponse) -> Unit,
    onPin: () -> Unit
) {
    val otherParticipant = conv.participants.firstOrNull { it.userId != currentUserId }
    val isGroup = conv.type == ConversationType.GROUP
    val contactName = if (!isGroup) {
        otherParticipant?.phoneNumber?.let { contactNameMap[it] }
    } else null
    val resolvedName = conv.name
        ?: contactName
        ?: otherParticipant?.displayName
        ?: otherParticipant?.phoneNumber
        ?: defaultChatName
    val isOtherOnline = otherParticipant?.let {
        onlineUsers[it.userId] ?: it.isOnline
    } ?: false
    val avatarUrl = if (isGroup) conv.avatarUrl else otherParticipant?.avatarUrl
    ConversationItem(
        conversation = conv,
        modifier = modifier,
        displayName = resolvedName,
        avatarUrl = avatarUrl,
        isOnline = isOtherOnline,
        isGroup = isGroup,
        isPinned = isPinned,
        onClick = { onConversationClick(conv.id, resolvedName, otherParticipant?.userId, isGroup) },
        onLongClick = { onConversationLongClick(conv) },
        onPin = onPin
    )
}

/** Localized labels for the conversation long-press action menu. */
internal data class ConversationActionLabels(
    val pin: String,
    val unpin: String,
    val archive: String,
    val unarchive: String,
    val mute: String,
    val unmute: String,
    val lock: String,
    val unlock: String,
    val delete: String,
    val cancel: String
)
