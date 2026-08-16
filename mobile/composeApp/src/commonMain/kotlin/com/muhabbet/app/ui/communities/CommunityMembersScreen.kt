package com.muhabbet.app.ui.communities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetEmptyState
import com.muhabbet.designsystem.components.MuhabbetErrorState
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.CommunityMemberResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Who is in a community.
 *
 * The screen works out for itself whether the viewer may add people, by finding their own row in
 * the list it already loaded. Passing that in as a navigation argument would have meant trusting a
 * value captured on a different screen at a different time; the list is the same source the server
 * authorises against.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityMembersScreen(
    communityId: String,
    onBack: () -> Unit,
    onMemberClick: (userId: String) -> Unit = {},
    communityRepository: CommunityRepository = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var members by remember { mutableStateOf<List<CommunityMemberResponse>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }

    val errorLoadMsg = stringResource(Res.string.error_load_failed)

    suspend fun loadMembers() {
        val failure = runCatchingCancellable {
            members = communityRepository.getCommunityMembers(communityId)
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        loadFailed = failure != null
        if (failure != null) {
            // Without this an authorisation failure renders as a community with no members,
            // which is indistinguishable from one the user has just been removed from.
            Log.e(TAG, "Failed to load members of community $communityId", failure)
            snackbarHostState.showSnackbar(errorLoadMsg)
        }
    }

    LaunchedEffect(communityId, retryKey) {
        isLoading = true
        loadMembers()
    }

    val loaded = members
    val canManage = loaded
        ?.firstOrNull { it.userId == currentUserId }
        ?.let { it.role == ROLE_OWNER || it.role == ROLE_ADMIN } == true

    if (showAddSheet) {
        AddCommunityMemberSheet(
            communityId = communityId,
            onDismiss = { showAddSheet = false },
            onMemberAdded = { scope.launch { loadMembers() } },
            snackbarHostState = snackbarHostState,
            communityRepository = communityRepository
        )
    }

    MuhabbetScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.community_members),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back),
                actions = {
                    if (canManage) {
                        MuhabbetIconButton(
                            icon = Muhabbet.icons.Add,
                            contentDescription = stringResource(Res.string.community_add_member),
                            onClick = { showAddSheet = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when {
            isLoading -> MuhabbetLoadingState(contentModifier)

            loadFailed -> MuhabbetErrorState(
                message = stringResource(Res.string.error_generic),
                modifier = contentModifier,
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = { retryKey++ }
            )

            loaded.isNullOrEmpty() -> MuhabbetEmptyState(
                modifier = contentModifier,
                icon = Muhabbet.icons.People,
                title = stringResource(Res.string.community_members_empty)
            )

            else -> LazyColumn(modifier = contentModifier) {
                items(loaded, key = { it.userId }) { member ->
                    CommunityMemberItem(
                        member = member,
                        isCurrentUser = member.userId == currentUserId,
                        onClick = { if (member.userId != currentUserId) onMemberClick(member.userId) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CommunityMemberItem(
    member: CommunityMemberResponse,
    isCurrentUser: Boolean,
    onClick: () -> Unit
) {
    val displayName = member.displayName ?: stringResource(Res.string.unknown)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUrl = member.avatarUrl,
            displayName = displayName,
            size = MuhabbetSizes.AvatarSmall
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName +
                    if (isCurrentUser) " " + stringResource(Res.string.group_member_you) else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
        RoleBadge(role = member.role)
    }
}

/**
 * Only OWNER and ADMIN are labelled. A badge on every plain member would be noise, and an unknown
 * role — the server can add one before the app ships an update — shows nothing rather than a raw
 * enum name.
 */
@Composable
private fun RoleBadge(role: String) {
    val label = when (role) {
        ROLE_OWNER -> stringResource(Res.string.group_role_owner)
        ROLE_ADMIN -> stringResource(Res.string.group_role_admin)
        else -> return
    }
    val background = if (role == ROLE_OWNER) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val foreground = if (role == ROLE_OWNER) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onTertiary
    }
    Row(horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)) {
        Surface(color = background, shape = MaterialTheme.shapes.extraSmall) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = foreground,
                modifier = Modifier.padding(
                    horizontal = MuhabbetSpacing.Small,
                    vertical = MuhabbetSizes.GapHairline
                )
            )
        }
    }
}

/**
 * The wire values of `MemberRole`. Compared as strings because the DTO carries the raw name: a role
 * the server adds later must not fail the whole decode on an app that has not shipped yet.
 */
internal const val ROLE_OWNER = "OWNER"
internal const val ROLE_ADMIN = "ADMIN"

private const val TAG = "CommunityMembersScreen"
