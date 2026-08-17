package com.muhabbet.app.ui.privacy

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
import com.muhabbet.app.data.repository.ModerationRepository
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.ConfirmDialog
import com.muhabbet.designsystem.components.MuhabbetEmptyState
import com.muhabbet.designsystem.components.MuhabbetErrorState
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.BlockedUserResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Everyone the signed-in user has blocked, with an unblock action per row. #613.
 *
 * Before this screen, the only way to reverse a block was to reach the profile of the person you
 * blocked — a screen you have little reason to open once you have blocked someone, and may not be
 * able to find at all if the conversation with them was deleted. `GET /api/v1/moderation/blocks`
 * used to return bare `blockedUserIds`; `ModerationController` now resolves a name and a face for
 * each one server-side, so this screen does not have to guess at a naming rule of its own.
 *
 * Deliberately does not also list *reports*: a report is a submission to us, not a state the user
 * owns, and it cannot be "undone" the way a block can. Showing a report row with no outcome to
 * report would be worse than not showing it — see the issue for the fuller reasoning. Nothing here
 * should grow a second tab for that without deciding what the status column says first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(
    onBack: () -> Unit,
    moderationRepository: ModerationRepository = koinInject()
) {
    var blockedUsers by remember { mutableStateOf<List<BlockedUserResponse>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }
    var pendingUnblock by remember { mutableStateOf<BlockedUserResponse?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val loadFailedMsg = stringResource(Res.string.blocked_users_load_failed)
    val unblockSuccessMsg = stringResource(Res.string.profile_unblock_success)
    val unblockFailedMsg = stringResource(Res.string.profile_unblock_failed)

    suspend fun load() {
        val failure = runCatchingCancellable {
            blockedUsers = moderationRepository.getBlockedUsers()
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        loadFailed = failure != null
        if (failure != null) {
            Log.e(TAG, "Failed to load blocked users", failure)
            snackbarHostState.showSnackbar(loadFailedMsg)
        }
    }

    LaunchedEffect(retryKey) {
        isLoading = true
        load()
    }

    pendingUnblock?.let { target ->
        ConfirmDialog(
            title = stringResource(Res.string.profile_unblock),
            message = stringResource(Res.string.profile_unblock_confirm),
            confirmLabel = stringResource(Res.string.profile_unblock),
            dismissLabel = stringResource(Res.string.cancel),
            onConfirm = {
                pendingUnblock = null
                scope.launch {
                    runCatchingCancellable { moderationRepository.unblockUser(target.userId) }
                        .onSuccess {
                            // Drop the row locally rather than re-fetching the whole list — the
                            // server has nothing more to tell us about a block that no longer exists.
                            blockedUsers = blockedUsers?.filterNot { it.userId == target.userId }
                            snackbarHostState.showSnackbar(unblockSuccessMsg)
                        }
                        .onFailure { e ->
                            Log.e(TAG, "Failed to unblock ${target.userId}", e)
                            snackbarHostState.showSnackbar(unblockFailedMsg)
                        }
                }
            },
            onDismiss = { pendingUnblock = null }
        )
    }

    MuhabbetScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.privacy_blocked_contacts),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        }
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        val loaded = blockedUsers
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
                icon = Muhabbet.icons.Block,
                title = stringResource(Res.string.blocked_users_empty),
                subtitle = stringResource(Res.string.blocked_users_empty_desc)
            )

            else -> LazyColumn(modifier = contentModifier) {
                items(loaded, key = { it.userId }) { blocked ->
                    BlockedUserItem(
                        blocked = blocked,
                        onUnblockClick = { pendingUnblock = blocked }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun BlockedUserItem(
    blocked: BlockedUserResponse,
    onUnblockClick: () -> Unit
) {
    // Same rule as everywhere else in the app: a user id is never a name (#507). A blocked user's
    // phone number is never available here either — GET /users/{id} withholds it for anyone but the
    // owner, so displayName (or this fallback) is the only rung this row has to stand on.
    val displayName = blocked.displayName?.ifBlank { null } ?: stringResource(Res.string.unknown_person)
    val unblockLabel = stringResource(Res.string.profile_unblock)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUrl = blocked.avatarUrl,
            displayName = displayName,
            size = MuhabbetSizes.AvatarSmall
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MuhabbetSizes.GapHairline)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = stringResource(Res.string.blocked_users_since, DateTimeFormatter.formatFullTimestamp(blocked.blockedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        MuhabbetIconButton(
            icon = Muhabbet.icons.Block,
            contentDescription = unblockLabel,
            tint = MaterialTheme.colorScheme.primary,
            onClick = onUnblockClick
        )
    }
}

private const val TAG = "BlockedUsersScreen"
