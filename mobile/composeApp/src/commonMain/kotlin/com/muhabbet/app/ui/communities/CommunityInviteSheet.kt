package com.muhabbet.app.ui.communities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.app.platform.rememberShareLauncher
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetBottomSheet
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.CommunityInviteLinkResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Mint, share and revoke a community's invite links (#387, #416).
 *
 * This sheet is the only place in the app from which a community can gain a member who is not
 * already in one of its groups. Every other route — the member picker, the owner-side add — is
 * restricted to people the server already considers adjacent (#375), which is why every community in
 * production has exactly one member.
 *
 * Reachable only when the viewer administers the community. That mirrors the server, which refuses
 * both the list and the create for a plain member: a token is a bearer credential, so being able to
 * read one is the same power as being able to admit anyone.
 *
 * The URL comes from the server rather than being assembled here, so the scheme can change without
 * shipping an app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityInviteSheet(
    communityId: String,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState,
    communityRepository: CommunityRepository = koinInject()
) {
    // null means "still loading", an empty list means "genuinely no links yet" — the two must not
    // render the same, or a slow network looks like an empty community.
    var links by remember { mutableStateOf<List<CommunityInviteLinkResponse>?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val shareLauncher = rememberShareLauncher()

    // stringResource is @Composable and cannot be called inside scope.launch — resolved here.
    val loadFailedMsg = stringResource(Res.string.error_load_failed)
    val copiedMsg = stringResource(Res.string.community_invite_copied)
    val createFailedMsg = stringResource(Res.string.community_invite_create_failed)
    val revokedMsg = stringResource(Res.string.community_invite_revoked)
    val revokeFailedMsg = stringResource(Res.string.community_invite_revoke_failed)

    suspend fun load() {
        val failure = runCatchingCancellable {
            links = communityRepository.getInviteLinks(communityId)
        }.exceptionOrNull()
        if (failure != null) {
            Log.e(TAG, "Failed to load invite links for $communityId", failure)
            // Leave the spinner rather than stranding it, then report.
            links = emptyList()
            snackbarHostState.showSnackbar(loadFailedMsg)
        }
    }

    LaunchedEffect(communityId) { load() }

    MuhabbetBottomSheet(onDismiss = onDismiss) {
        Text(
            text = stringResource(Res.string.community_invite_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(MuhabbetSpacing.XSmall))
        Text(
            text = stringResource(Res.string.community_invite_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(MuhabbetSpacing.Large))

        val current = links
        when {
            current == null -> MuhabbetLoadingState(
                Modifier.fillMaxWidth().padding(MuhabbetSpacing.XLarge)
            )

            current.isEmpty() -> Text(
                text = stringResource(Res.string.community_invite_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = MuhabbetSpacing.Medium)
            )

            else -> LazyColumn(modifier = Modifier.heightIn(max = MuhabbetSizes.PickerSheetMaxHeight)) {
                items(current, key = { it.id }) { link ->
                    InviteLinkRow(
                        link = link,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(link.inviteUrl))
                            scope.launch { snackbarHostState.showSnackbar(copiedMsg) }
                        },
                        onShare = { shareLauncher(link.inviteUrl) },
                        onRevoke = {
                            scope.launch {
                                isBusy = true
                                val failure = runCatchingCancellable {
                                    communityRepository.revokeInviteLink(communityId, link.id)
                                    load()
                                }.exceptionOrNull()
                                // Clear the spinner BEFORE reporting — showSnackbar suspends until
                                // dismissed (~4s).
                                isBusy = false
                                if (failure != null) {
                                    Log.e(TAG, "Failed to revoke invite link ${link.id}", failure)
                                }
                                snackbarHostState.showSnackbar(
                                    if (failure == null) revokedMsg else revokeFailedMsg
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(MuhabbetSpacing.Large))
        MuhabbetButton(
            text = stringResource(Res.string.community_invite_create),
            enabled = !isBusy && current != null,
            onClick = {
                scope.launch {
                    isBusy = true
                    val failure = runCatchingCancellable {
                        // Deliberately no maxUses and no expiry: those are extra decisions to make
                        // before the first link exists, and the link is revocable from this same
                        // sheet. The server accepts both, so a picker can be added without a
                        // backend change if anyone actually asks for one.
                        communityRepository.createInviteLink(communityId)
                        load()
                    }.exceptionOrNull()
                    isBusy = false
                    if (failure != null) {
                        Log.e(TAG, "Failed to create an invite link for $communityId", failure)
                        snackbarHostState.showSnackbar(createFailedMsg)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(MuhabbetSpacing.Large))
    }
}

@Composable
private fun InviteLinkRow(
    link: CommunityInviteLinkResponse,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRevoke: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = MuhabbetSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.inviteUrl,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = link.maxUses?.let {
                    stringResource(Res.string.community_invite_uses_limited, link.useCount, it)
                } ?: stringResource(Res.string.community_invite_uses, link.useCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        MuhabbetIconButton(
            icon = Muhabbet.icons.Copy,
            contentDescription = stringResource(Res.string.community_invite_copy),
            onClick = onCopy
        )
        MuhabbetIconButton(
            icon = Muhabbet.icons.Share,
            contentDescription = stringResource(Res.string.community_invite_share),
            onClick = onShare
        )
        MuhabbetIconButton(
            icon = Muhabbet.icons.Delete,
            contentDescription = stringResource(Res.string.community_invite_revoke),
            onClick = onRevoke,
            tint = MaterialTheme.colorScheme.error
        )
    }
}

private const val TAG = "CommunityInviteSheet"
