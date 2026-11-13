package com.muhabbet.app.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetBottomSheet
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.SettingsNavRow
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.JoinRequestResponse
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.MemberRole
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.muhabbet.designsystem.components.ConfirmDialog
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A plain "icon + label" navigation row on the group info screen (shared media, invite link,
 * events). Was the same 22 lines written out three times.
 */
@Composable
internal fun GroupInfoLinkRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(MuhabbetSizes.IconLarge)
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * The member action sheet and the removal confirmation (#788). Removal is never one tap: the sheet
 * offers it, and this dialog asks before anything is sent.
 */
@Composable
internal fun MemberManagementDialogs(
    actionTarget: ParticipantResponse?,
    removeTarget: ParticipantResponse?,
    myRole: MemberRole,
    currentUserId: String,
    onDismissSheet: () -> Unit,
    onAction: (ParticipantResponse, MemberAction) -> Unit,
    onConfirmRemove: (ParticipantResponse) -> Unit,
    onDismissRemove: () -> Unit
) {
    actionTarget?.let { member ->
        MemberActionSheet(
            memberName = member.displayName ?: member.phoneNumber ?: stringResource(Res.string.unknown),
            actions = memberActions(myRole, member.role, isSelf = member.userId == currentUserId),
            onDismiss = onDismissSheet,
            onAction = { action -> onAction(member, action) }
        )
    }
    removeTarget?.let { member ->
        val name = member.displayName ?: member.phoneNumber ?: stringResource(Res.string.unknown)
        ConfirmDialog(
            title = stringResource(Res.string.group_remove_member),
            message = stringResource(Res.string.group_remove_confirm, name),
            confirmLabel = stringResource(Res.string.group_remove_member),
            dismissLabel = stringResource(Res.string.cancel),
            onConfirm = { onConfirmRemove(member) },
            onDismiss = onDismissRemove,
            isDestructive = true
        )
    }
}

/** The pending join requests block, shown only when there are any (and only admins are sent any). */
internal fun LazyListScope.joinRequestsSection(
    requests: List<JoinRequestResponse>,
    busy: Boolean,
    onAnswer: (request: JoinRequestResponse, approve: Boolean) -> Unit
) {
    if (requests.isEmpty()) return
    item {
        Text(
            text = pluralStringResource(Res.plurals.group_join_requests_header, requests.size, requests.size),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium)
        )
    }
    items(requests, key = { "join-${it.id}" }) { request ->
        JoinRequestRow(
            request = request,
            busy = busy,
            onApprove = { onAnswer(request, true) },
            onReject = { onAnswer(request, false) }
        )
    }
    item { HorizontalDivider() }
}

/**
 * One member row. Tapping it is the only interaction (#788): the trash icon that removed a member on
 * a single unconfirmed tap is gone, and every action — including removal, behind a confirmation —
 * now lives in [MemberActionSheet], offered according to [memberActions].
 */
@Composable
internal fun MemberItem(
    member: ParticipantResponse,
    isCurrentUser: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUrl = member.avatarUrl,
            displayName = member.displayName ?: "?",
            size = MuhabbetSizes.AvatarSmall
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = (member.displayName ?: member.phoneNumber ?: stringResource(Res.string.unknown)).let { name ->
                        if (isCurrentUser) stringResource(Res.string.group_member_you, name) else name
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (member.role == MemberRole.OWNER) {
                    RoleBadge(
                        label = stringResource(Res.string.group_role_owner),
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary
                    )
                } else if (member.role == MemberRole.ADMIN) {
                    RoleBadge(
                        label = stringResource(Res.string.group_role_admin),
                        container = MaterialTheme.colorScheme.tertiary,
                        content = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }
            val phone = member.phoneNumber
            if (phone != null) {
                Text(
                    text = phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RoleBadge(label: String, container: androidx.compose.ui.graphics.Color, content: androidx.compose.ui.graphics.Color) {
    Surface(color = container, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = MuhabbetSizes.GapHairline)
        )
    }
}

/**
 * The actions available on one member, as rows in a sheet. [actions] comes from [memberActions], so
 * the sheet never offers something the server would refuse.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemberActionSheet(
    memberName: String,
    actions: List<MemberAction>,
    onAction: (MemberAction) -> Unit,
    onDismiss: () -> Unit
) {
    MuhabbetBottomSheet(onDismiss = onDismiss) {
        Text(
            text = memberName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = MuhabbetSpacing.Small)
        )
        actions.forEach { action ->
            when (action) {
                MemberAction.VIEW_PROFILE -> SettingsNavRow(
                    title = stringResource(Res.string.group_member_action_view_profile),
                    icon = Muhabbet.icons.Person,
                    onClick = { onAction(action) }
                )
                MemberAction.MAKE_ADMIN -> SettingsNavRow(
                    title = stringResource(Res.string.group_member_action_make_admin),
                    icon = Muhabbet.icons.Moderation,
                    onClick = { onAction(action) }
                )
                MemberAction.DISMISS_ADMIN -> SettingsNavRow(
                    title = stringResource(Res.string.group_member_action_dismiss_admin),
                    icon = Muhabbet.icons.Moderation,
                    onClick = { onAction(action) }
                )
                MemberAction.REMOVE -> SettingsNavRow(
                    title = stringResource(Res.string.group_remove_member),
                    icon = Muhabbet.icons.Delete,
                    destructive = true,
                    onClick = { onAction(action) }
                )
            }
        }
    }
}

/**
 * One pending invite-link request, with Approve and Decline (#788 — these existed on the server and
 * nowhere in the app). [busy] disables both while either answer is in flight, so a double tap
 * cannot send both.
 */
@Composable
internal fun JoinRequestRow(
    request: JoinRequestResponse,
    busy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val name = request.displayName ?: stringResource(Res.string.unknown)
        UserAvatar(avatarUrl = request.avatarUrl, displayName = name, size = MuhabbetSizes.AvatarSmall)
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        MuhabbetButton(
            text = stringResource(Res.string.group_join_request_reject),
            onClick = onReject,
            role = MuhabbetButtonRole.Text,
            enabled = !busy
        )
        MuhabbetButton(
            text = stringResource(Res.string.group_join_request_approve),
            onClick = onApprove,
            role = MuhabbetButtonRole.Secondary,
            enabled = !busy
        )
    }
}
