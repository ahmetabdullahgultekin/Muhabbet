package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.muhabbet.shared.dto.UserStatusGroup
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.designsystem.components.EmptyChatsIllustration
import com.muhabbet.designsystem.components.MuhabbetSkeletonList
import com.muhabbet.designsystem.components.rememberSkeletonVisible
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/*
 * The list itself, split out of ConversationListScreen when that file passed 750 lines — well
 * over the 500-line guideline. The screen keeps the state and the effects; this keeps the
 * rendering of what that state produces.
 */

/**
 * Loading / empty / list body for [ConversationListScreen]. Keeps archived chats out of the list
 * behind a single [ArchivedChatsRow] at the top (#612), sorts pinned-first, and renders the status
 * row + filter chips above the list.
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
    onConversationClick: (ChatTarget) -> Unit,
    onConversationLongClick: (ConversationResponse) -> Unit,
    onPin: (ConversationResponse) -> Unit,
    onOpenArchived: () -> Unit
) {
    val showSkeleton = rememberSkeletonVisible(isLoading)
    val loadingLabel = stringResource(Res.string.chats_loading)

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        // MuhabbetSkeletonList, not a LazyColumn of hand-rolled rows: it wraps its rows in a
        // single ShimmerHost. The old version called ConversationSkeletonItem per row, and that
        // function created its own rememberInfiniteTransition — eight placeholder rows meant
        // eight independent infinite animations, each waking the frame clock on its own.
        //
        // Behind MuhabbetSkeletonGate since #499: this list is served from the SQLDelight cache on
        // a warm start, which resolves in a few milliseconds, and the skeleton was appearing and
        // vanishing inside a single blink.
        if (showSkeleton) {
            MuhabbetSkeletonList(modifier = Modifier.fillMaxSize(), loadingLabel = loadingLabel)
        } else if (isLoading) {
            // Under the appear delay. Blank rather than falling through to the empty state, which
            // would tell someone with a full inbox that they have no chats.
        } else if (conversations.isEmpty()) {
            EmptyChatsIllustration(
                title = stringResource(Res.string.empty_chats_title),
                subtitle = stringResource(Res.string.empty_chats_subtitle),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Which chats belong in the list, and how many are behind the archived row — one rule,
            // in one testable place. See [conversationSections].
            val sections = conversationSections(conversations, activeFilter)

            LazyColumn {
                // Pinned at the very top, above the status row and every chat — the fix for #612.
                // The old archived section sat below every active conversation (invisible on any real
                // account) and rendered nothing at all when the archive was empty (nothing to
                // discover before you had already used the feature). Shown only once something is
                // archived: the long-press menu already always offers "Archive", so this row's job is
                // solely "where did it go", which has nothing to say at zero.
                if (sections.archivedCount > 0) {
                    item(key = "archived_row") {
                        ArchivedChatsRow(count = sections.archivedCount, onClick = onOpenArchived)
                        HorizontalDivider()
                    }
                }
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
                items(sections.active, key = { it.id }) { conv ->
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
    onConversationClick: (ChatTarget) -> Unit,
    onConversationLongClick: (ConversationResponse) -> Unit,
    onPin: () -> Unit
) {
    val otherParticipant = conv.participants.firstOrNull { it.userId != currentUserId }
    // One rule, shared with every other screen that opens a chat — the row and the tap can no
    // longer disagree about who this is, and neither can this screen and Starred Messages (#543).
    val target = conv.toChatTarget(currentUserId, defaultChatName, contactNameMap)
    val isOtherOnline = otherParticipant?.let {
        onlineUsers[it.userId] ?: it.isOnline
    } ?: false
    ConversationItem(
        conversation = conv,
        modifier = modifier,
        displayName = target.name,
        avatarUrl = target.avatarUrl,
        isOnline = isOtherOnline,
        isGroup = target.isGroup,
        isPinned = isPinned,
        onClick = { onConversationClick(target) },
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
