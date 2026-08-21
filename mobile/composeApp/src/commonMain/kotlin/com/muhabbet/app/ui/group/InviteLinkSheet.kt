package com.muhabbet.app.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.muhabbet.app.data.repository.InviteLinkRepository
import com.muhabbet.app.platform.rememberShareLauncher
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.CreateInviteLinkRequest
import com.muhabbet.shared.dto.InviteLinkResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetBottomSheet
import com.muhabbet.designsystem.components.MuhabbetSwitch
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.theme.containerColor
import com.muhabbet.designsystem.theme.depth
import com.muhabbet.designsystem.theme.MuhabbetDepth

/**
 * @param canManage whether the viewer may create or revoke the link — admins and owners only.
 * Every member may *read* it, because a member who cannot see the link cannot invite anyone with
 * it. The two controls a member does not get are hidden rather than shown and refused: the server
 * answers 403 for both, and a button that always fails is worse than no button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteLinkSheet(
    conversationId: String,
    canManage: Boolean,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState,
    inviteLinkRepository: InviteLinkRepository = koinInject()
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val shareLauncher = rememberShareLauncher()
    var inviteLink by remember { mutableStateOf<InviteLinkResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var requireApproval by remember { mutableStateOf(false) }

    val linkCopiedMsg = stringResource(Res.string.invite_link_copied)
    val genericErrorMsg = stringResource(Res.string.error_generic)

    LaunchedEffect(conversationId) {
        val failure = runCatchingCancellable {
            inviteLink = inviteLinkRepository.getInviteLink(conversationId)
            requireApproval = inviteLink?.requiresApproval ?: false
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        if (failure != null) {
            // A group with no link answers 404 and the repository maps that to null, so anything
            // reaching here is a real failure. Left unreported the sheet shows the "create a link"
            // branch, which invites a user without the right to create one to try.
            Log.e(TAG, "Failed to load invite link", failure)
            snackbarHostState.showSnackbar(genericErrorMsg)
        }
    }

    MuhabbetBottomSheet(onDismiss = onDismiss) {
        Column {
            Text(
                text = stringResource(Res.string.invite_link_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(MuhabbetSpacing.Large))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(MuhabbetSizes.IconLarge).align(Alignment.CenterHorizontally))
            } else if (inviteLink != null) {
                val link = inviteLink ?: return@Column

                // Show link
                val linkCardShape = MaterialTheme.shapes.medium
                Surface(
                    color = MuhabbetDepth.Raised.containerColor(),
                    shape = linkCardShape,
                    modifier = Modifier.fillMaxWidth().depth(MuhabbetDepth.Raised, linkCardShape)
                ) {
                    Row(
                        modifier = Modifier.padding(MuhabbetSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Muhabbet.icons.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(MuhabbetSizes.IconMedium)
                        )
                        Spacer(Modifier.width(MuhabbetSpacing.Small))
                        Text(
                            text = link.inviteUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(MuhabbetSpacing.Large))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Copy
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MuhabbetIconButton(
                            icon = Muhabbet.icons.Copy,
                            contentDescription = stringResource(Res.string.invite_link_copy),
                            onClick = {
                            clipboardManager.setText(AnnotatedString(link.inviteUrl))
                            scope.launch { snackbarHostState.showSnackbar(linkCopiedMsg) }
                        }
                        )
                        Text(
                            text = stringResource(Res.string.invite_link_copy),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    // Share
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MuhabbetIconButton(
                            icon = Muhabbet.icons.Share,
                            contentDescription = stringResource(Res.string.invite_link_share),
                            onClick = { shareLauncher(link.inviteUrl) }
                        )
                        Text(
                            text = stringResource(Res.string.invite_link_share),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    // Revoke — admins and owners only; the server answers 403 for anyone else.
                    if (canManage) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MuhabbetIconButton(
                                icon = Muhabbet.icons.Delete,
                                contentDescription = stringResource(Res.string.invite_link_revoke),
                                onClick = {
                                    scope.launch {
                                        try {
                                            inviteLinkRepository.revokeInviteLink(conversationId, link.id)
                                            inviteLink = null
                                        } catch (_: Exception) {
                                            snackbarHostState.showSnackbar(genericErrorMsg)
                                        }
                                    }
                                },
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = stringResource(Res.string.invite_link_revoke),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else if (!canManage) {
                // A member on a group whose admins have not made a link. Saying so is the honest
                // end of the road — offering Create here would only produce a 403.
                Text(
                    text = stringResource(Res.string.invite_link_none_yet),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // No link yet - create one
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.invite_link_require_approval),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    MuhabbetSwitch(
                        checked = requireApproval,
                        onCheckedChange = { requireApproval = it }
                    )
                }

                Spacer(Modifier.height(MuhabbetSpacing.Large))

                MuhabbetButton(
                    text = stringResource(Res.string.invite_link_create),
                    onClick = {
                        scope.launch {
                            isLoading = true
                            var createFailed = false
                            try {
                                inviteLink = inviteLinkRepository.createInviteLink(
                                    conversationId,
                                    CreateInviteLinkRequest(requiresApproval = requireApproval)
                                )
                            } catch (_: Exception) {
                                createFailed = true
                            }
                            // Clear the spinner BEFORE reporting — showSnackbar suspends until
                            // dismissed (~4s).
                            isLoading = false
                            if (createFailed) snackbarHostState.showSnackbar(genericErrorMsg)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    role = MuhabbetButtonRole.Primary
                )
            }

            Spacer(Modifier.height(MuhabbetSpacing.XLarge))
        }
    }
}

private const val TAG = "InviteLinkSheet"
