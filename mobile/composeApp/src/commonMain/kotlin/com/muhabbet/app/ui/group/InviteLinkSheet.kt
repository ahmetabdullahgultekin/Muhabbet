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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.InviteLinkRepository
import com.muhabbet.app.ui.theme.MuhabbetElevation
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.CreateInviteLinkRequest
import com.muhabbet.shared.dto.InviteLinkResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteLinkSheet(
    conversationId: String,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState,
    inviteLinkRepository: InviteLinkRepository = koinInject()
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var inviteLink by remember { mutableStateOf<InviteLinkResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var requireApproval by remember { mutableStateOf(false) }

    val linkCopiedMsg = stringResource(Res.string.invite_link_copied)
    val genericErrorMsg = stringResource(Res.string.error_generic)

    LaunchedEffect(conversationId) {
        try {
            inviteLink = inviteLinkRepository.getInviteLink(conversationId)
            requireApproval = inviteLink?.requiresApproval ?: false
        } catch (_: Exception) { }
        isLoading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.padding(MuhabbetSpacing.XLarge)
        ) {
            Text(
                text = stringResource(Res.string.invite_link_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(MuhabbetSpacing.Large))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
            } else if (inviteLink != null) {
                val link = inviteLink ?: return@Column

                // Show link
                Surface(
                    tonalElevation = MuhabbetElevation.Level1,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(MuhabbetSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
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
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(link.inviteUrl))
                            scope.launch { snackbarHostState.showSnackbar(linkCopiedMsg) }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(Res.string.invite_link_copy))
                        }
                        Text(
                            text = stringResource(Res.string.invite_link_copy),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    // Share
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { /* TODO: platform share */ }) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(Res.string.invite_link_share))
                        }
                        Text(
                            text = stringResource(Res.string.invite_link_share),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    // Revoke
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = {
                            scope.launch {
                                try {
                                    inviteLinkRepository.revokeInviteLink(conversationId, link.id)
                                    inviteLink = null
                                } catch (_: Exception) {
                                    snackbarHostState.showSnackbar(genericErrorMsg)
                                }
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.invite_link_revoke), tint = MaterialTheme.colorScheme.error)
                        }
                        Text(
                            text = stringResource(Res.string.invite_link_revoke),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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
                    Switch(
                        checked = requireApproval,
                        onCheckedChange = { requireApproval = it }
                    )
                }

                Spacer(Modifier.height(MuhabbetSpacing.Large))

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            try {
                                inviteLink = inviteLinkRepository.createInviteLink(
                                    conversationId,
                                    CreateInviteLinkRequest(requiresApproval = requireApproval)
                                )
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar(genericErrorMsg)
                            }
                            isLoading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(Res.string.invite_link_create))
                }
            }

            Spacer(Modifier.height(MuhabbetSpacing.XLarge))
        }
    }
}
