package com.muhabbet.app.ui.communities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.ui.conversations.ChatTarget
import com.muhabbet.shared.dto.CommunityDetailResponse
import com.muhabbet.shared.dto.CommunityGroupInfo
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The scrolling body of a community: who it is, how to reach its members, and its groups.
 *
 * Split from [CommunityDetailScreen] so that screen is left with what it is actually responsible
 * for — loading, the dialogs and the network calls behind them. Purely presentational: every action
 * is a callback, so nothing here knows about repositories or navigation.
 *
 * @param canManage whether the viewer is an OWNER or ADMIN. Controls that would 403 are not drawn
 * at all rather than drawn and then refused.
 */
@Composable
fun CommunityDetailContent(
    community: CommunityDetailResponse,
    canManage: Boolean,
    contentPadding: PaddingValues,
    onMembersClick: () -> Unit,
    onInviteClick: () -> Unit,
    onAddGroupClick: () -> Unit,
    onGroupClick: (ChatTarget) -> Unit,
    onRemoveGroupClick: (CommunityGroupInfo) -> Unit
) {
    // Resolved here rather than in the row's onClick: stringResource is @Composable and a click
    // handler is not.
    val defaultChatName = stringResource(Res.string.chat_default_name)
    LazyColumn(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        item {
            CommunityHeader(community)
            HorizontalDivider()
        }

        // The announcement channel (#584) — every member is in it, only admins/owners can post,
        // and it is the one place a community is actually a place to talk rather than a container
        // of groups. Null only for a community read by a server that predates this field; the
        // service backfills it on the very next read, so this is a decode-safety fallback, not a
        // state a real community stays in.
        community.announcementGroupId?.let { announcementGroupId ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            onClick = {
                                onGroupClick(
                                    ChatTarget(
                                        conversationId = announcementGroupId,
                                        name = community.name,
                                        isGroup = true,
                                        avatarUrl = community.avatarUrl
                                    )
                                )
                            }
                        )
                        .padding(horizontal = MuhabbetSpacing.XLarge, vertical = MuhabbetSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Muhabbet.icons.Channel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(MuhabbetSizes.IconLarge)
                    )
                    Spacer(Modifier.width(MuhabbetSpacing.Medium))
                    Text(
                        text = stringResource(Res.string.community_announcements),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Medium))
            }
        }

        // Members row — the only route to the member list, and so the only place from which a
        // person can be added to a community at all.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onMembersClick)
                    .padding(horizontal = MuhabbetSpacing.XLarge, vertical = MuhabbetSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Muhabbet.icons.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(MuhabbetSizes.IconLarge)
                )
                Spacer(Modifier.width(MuhabbetSpacing.Medium))
                Text(
                    text = stringResource(Res.string.community_members),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = pluralStringResource(
                        Res.plurals.community_member_count,
                        community.memberCount,
                        community.memberCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
            Spacer(Modifier.height(MuhabbetSpacing.Medium))
        }

        // Invite via link (#387, #416) — admins and owners only, matching the server, which refuses
        // both the list and the create for a plain member. This is the only control in the app that
        // can bring in someone who is not already in one of the community's groups.
        if (canManage) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onInviteClick)
                        .padding(horizontal = MuhabbetSpacing.XLarge, vertical = MuhabbetSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Muhabbet.icons.Link,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(MuhabbetSizes.IconLarge)
                    )
                    Spacer(Modifier.width(MuhabbetSpacing.Medium))
                    Text(
                        text = stringResource(Res.string.community_invite_row),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Medium))
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MuhabbetSpacing.XLarge, vertical = MuhabbetSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(Res.string.community_groups),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = pluralStringResource(
                            Res.plurals.community_group_count,
                            community.groups.size,
                            community.groups.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canManage) {
                    TextButton(onClick = onAddGroupClick) {
                        Icon(
                            Muhabbet.icons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(MuhabbetSizes.IconSmall)
                        )
                        Spacer(Modifier.width(MuhabbetSpacing.XSmall))
                        Text(stringResource(Res.string.community_add_group))
                    }
                }
            }
        }

        items(community.groups, key = { it.conversationId }) { group ->
            GroupItem(
                group = group,
                canRemove = canManage,
                onClick = {
                    // `group.name.orEmpty()` here was #543 wearing a different hat: an unnamed
                    // group opened a chat titled "". The group's own picture travels too, so the
                    // chat header does not fall back to the name-seeded gradient.
                    onGroupClick(
                        ChatTarget(
                            conversationId = group.conversationId,
                            name = group.name ?: defaultChatName,
                            isGroup = true,
                            avatarUrl = group.avatarUrl
                        )
                    )
                },
                onRemove = { onRemoveGroupClick(group) }
            )
        }
    }
}

@Composable
private fun CommunityHeader(community: CommunityDetailResponse) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MuhabbetSpacing.XLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UserAvatar(
            avatarUrl = community.avatarUrl,
            displayName = community.name,
            size = MuhabbetSizes.AvatarXXLarge
        )
        Spacer(Modifier.height(MuhabbetSpacing.Medium))
        Text(
            text = community.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        community.description?.let { description ->
            Spacer(Modifier.height(MuhabbetSpacing.Small))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(MuhabbetSpacing.Small))
        Text(
            text = pluralStringResource(
                Res.plurals.community_member_count,
                community.memberCount,
                community.memberCount
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GroupItem(
    group: CommunityGroupInfo,
    canRemove: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        tonalElevation = MuhabbetElevation.Level1
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MuhabbetSpacing.XLarge,
                vertical = MuhabbetSpacing.Medium
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                avatarUrl = group.avatarUrl,
                displayName = group.name ?: "",
                size = MuhabbetSizes.AvatarMedium
            )
            Spacer(Modifier.width(MuhabbetSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    text = pluralStringResource(
                        Res.plurals.community_member_count,
                        group.memberCount,
                        group.memberCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canRemove) {
                MuhabbetIconButton(
                    icon = Muhabbet.icons.Delete,
                    contentDescription = stringResource(Res.string.community_remove_group),
                    onClick = onRemove,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
