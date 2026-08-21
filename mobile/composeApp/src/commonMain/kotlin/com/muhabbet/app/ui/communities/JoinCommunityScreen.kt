package com.muhabbet.app.ui.communities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextAlign
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetErrorState
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.CommunityInvitePreviewResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The accept step — what someone holding an invite link sees, and the action that actually makes
 * them a member (#387).
 *
 * This screen is the whole reason the invite is an invite rather than an owner-side add. #375 had to
 * restrict `addMember` because a community could be attached to any user id an owner could guess,
 * and that person would find it in their Communities tab having never been shown anything. Here they
 * are shown what they are joining, by whom, and how big it is — and nothing is written until they
 * press the button.
 *
 * What the server sends back is deliberately thin: name, avatar, member count, inviter. Not the
 * group list, not the member list. Those still require membership, which is exactly what #375 fixed
 * and what this screen must not undo.
 *
 * [CommunityInvitePreviewResponse.alreadyMember] exists so that re-opening a link you were sent
 * offers "open" rather than an accept that would fail — tapping a link twice is an ordinary thing to
 * do, not an error.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinCommunityScreen(
    token: String,
    onBack: () -> Unit,
    onJoined: (communityId: String) -> Unit,
    communityRepository: CommunityRepository = koinInject()
) {
    var preview by remember { mutableStateOf<CommunityInvitePreviewResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var isJoining by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val joinFailedMsg = stringResource(Res.string.community_join_failed)

    LaunchedEffect(token, retryKey) {
        isLoading = true
        loadFailed = false
        val failure = runCatchingCancellable {
            preview = communityRepository.previewInvite(token)
        }.exceptionOrNull()
        isLoading = false
        if (failure != null) {
            // A revoked, expired or used-up link lands here. Saying so is the point — the previous
            // behaviour for an unusable link was no screen at all.
            Log.e(TAG, "Failed to preview community invite", failure)
            loadFailed = true
        }
    }

    MuhabbetScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.community_join_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        }
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        val current = preview
        when {
            isLoading -> MuhabbetLoadingState(contentModifier)

            loadFailed || current == null -> MuhabbetErrorState(
                message = stringResource(Res.string.community_join_invalid),
                modifier = contentModifier,
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = { retryKey++ }
            )

            else -> InvitePreviewContent(
                preview = current,
                isJoining = isJoining,
                modifier = contentModifier,
                onAccept = {
                    scope.launch {
                        isJoining = true
                        val joined = runCatchingCancellable {
                            communityRepository.acceptInvite(token)
                        }
                        isJoining = false
                        val community = joined.getOrNull()
                        if (community != null) {
                            onJoined(community.id)
                        } else {
                            Log.e(TAG, "Failed to accept community invite", joined.exceptionOrNull())
                            snackbarHostState.showSnackbar(joinFailedMsg)
                        }
                    }
                },
                onOpen = { onJoined(current.communityId) }
            )
        }
    }
}

@Composable
private fun InvitePreviewContent(
    preview: CommunityInvitePreviewResponse,
    isJoining: Boolean,
    modifier: Modifier,
    onAccept: () -> Unit,
    onOpen: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = MuhabbetSpacing.XLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        UserAvatar(
            avatarUrl = preview.avatarUrl,
            displayName = preview.name,
            size = MuhabbetSizes.AvatarLarge
        )
        Spacer(Modifier.height(MuhabbetSpacing.Large))
        Text(
            text = preview.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(MuhabbetSpacing.XSmall))
        Text(
            text = stringResource(Res.string.community_join_member_count, preview.memberCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Only when the server resolved a name. An invite from someone whose display name is unknown
        // says nothing rather than "invited you by null".
        preview.inviterDisplayName?.let { inviter ->
            Spacer(Modifier.height(MuhabbetSpacing.XSmall))
            Text(
                text = stringResource(Res.string.community_join_invited_by, inviter),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))

        if (preview.alreadyMember) {
            Text(
                text = stringResource(Res.string.community_join_already_member),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(MuhabbetSpacing.Medium))
            MuhabbetButton(
                text = stringResource(Res.string.community_join_open),
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            MuhabbetButton(
                text = stringResource(Res.string.community_join_button),
                enabled = !isJoining,
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private const val TAG = "JoinCommunityScreen"
