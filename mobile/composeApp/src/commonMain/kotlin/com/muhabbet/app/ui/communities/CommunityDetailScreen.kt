package com.muhabbet.app.ui.communities

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.ConfirmDialog
import com.muhabbet.designsystem.components.MuhabbetErrorState
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.app.ui.conversations.ChatTarget
import com.muhabbet.shared.dto.CommunityDetailResponse
import com.muhabbet.shared.dto.CommunityGroupInfo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityDetailScreen(
    communityId: String,
    onBack: () -> Unit,
    onGroupClick: (ChatTarget) -> Unit,
    onMembersClick: (String) -> Unit,
    communityRepository: CommunityRepository = koinInject()
) {
    var detail by remember { mutableStateOf<CommunityDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddGroupSheet by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    // #446: only the server knows whether a name is already taken, so this is filled in after a
    // failed save and shown against the dialog's name field rather than as a snackbar.
    var editNameError by remember { mutableStateOf<String?>(null) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var groupPendingRemoval by remember { mutableStateOf<CommunityGroupInfo?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val errorLoadMsg = stringResource(Res.string.error_load_failed)
    val updatedMsg = stringResource(Res.string.community_updated)
    val updateFailedMsg = stringResource(Res.string.community_update_failed)
    val nameTakenMsg = stringResource(Res.string.community_name_taken)
    val leaveFailedMsg = stringResource(Res.string.community_leave_failed)
    val groupRemovedMsg = stringResource(Res.string.community_remove_group_removed)
    val groupRemoveFailedMsg = stringResource(Res.string.community_remove_group_failed)
    val deleteFailedMsg = stringResource(Res.string.community_delete_failed)

    suspend fun loadDetail() {
        val failure = runCatchingCancellable {
            detail = communityRepository.getCommunityDetail(communityId)
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s), and
        // this used to sit between the failure and the `isLoading = false` at the call site.
        isLoading = false
        if (failure != null) {
            // Without this the screen sits on a generic title with no groups, indistinguishable
            // from an empty community.
            Log.e(TAG, "Failed to load community $communityId", failure)
            snackbarHostState.showSnackbar(errorLoadMsg)
        }
    }

    // Bumped by the error state's retry button, so a failed load is no longer terminal.
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(communityId, retryKey) {
        isLoading = true
        loadDetail()
    }

    val current = detail
    // Only OWNER and ADMIN may change a community; the server enforces it, and hiding the controls
    // keeps a plain member from being shown buttons that would 403.
    val canManage = current?.myRole == ROLE_OWNER || current?.myRole == ROLE_ADMIN
    // Delete is stricter still — owner only (#407), unlike rename/add-group which admins may also do.
    val isOwner = current?.myRole == ROLE_OWNER

    if (showAddGroupSheet && current != null) {
        AddGroupToCommunitySheet(
            communityId = communityId,
            excludeConversationIds = current.groups.map { it.conversationId }.toSet(),
            onDismiss = { showAddGroupSheet = false },
            onGroupAdded = { scope.launch { loadDetail() } },
            snackbarHostState = snackbarHostState,
            communityRepository = communityRepository
        )
    }

    if (showEditDialog && current != null) {
        EditCommunityDialog(
            initialName = current.name,
            initialDescription = current.description,
            onDismiss = {
                showEditDialog = false
                editNameError = null
            },
            nameError = editNameError,
            onNameEdited = { editNameError = null },
            onConfirm = { newName, newDescription ->
                scope.launch {
                    val failure = runCatchingCancellable {
                        communityRepository.updateCommunity(communityId, newName, newDescription)
                        loadDetail()
                    }.exceptionOrNull()
                    if (failure != null) {
                        Log.e(TAG, "Failed to update community $communityId", failure)
                    }
                    // A name already in use is the one failure the user can fix without leaving the
                    // dialog, so the dialog stays up and says which field is wrong. Every other
                    // outcome — success or a failure this screen cannot explain — closes it and
                    // reports in the snackbar, as before.
                    if ((failure as? ApiException)?.code == CommunityNameTakenCode) {
                        editNameError = nameTakenMsg
                        return@launch
                    }
                    showEditDialog = false
                    editNameError = null
                    snackbarHostState.showSnackbar(if (failure == null) updatedMsg else updateFailedMsg)
                }
            }
        )
    }

    if (showLeaveDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.community_leave_title),
            message = stringResource(Res.string.community_leave_confirm),
            confirmLabel = stringResource(Res.string.community_leave_button),
            dismissLabel = stringResource(Res.string.cancel),
            isDestructive = true,
            onDismiss = { showLeaveDialog = false },
            onConfirm = {
                showLeaveDialog = false
                scope.launch {
                    val failure = runCatchingCancellable {
                        communityRepository.leaveCommunity(communityId)
                    }.exceptionOrNull()
                    if (failure == null) {
                        onBack()
                    } else {
                        // The server refuses the community's only member, because leaving would
                        // strand rows nothing can reach. The user has to be told, not silently
                        // returned to a list that still shows the community.
                        Log.e(TAG, "Failed to leave community $communityId", failure)
                        snackbarHostState.showSnackbar(leaveFailedMsg)
                    }
                }
            }
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.community_delete_title),
            message = stringResource(Res.string.community_delete_confirm),
            confirmLabel = stringResource(Res.string.community_delete_button),
            dismissLabel = stringResource(Res.string.cancel),
            isDestructive = true,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    val failure = runCatchingCancellable {
                        communityRepository.deleteCommunity(communityId)
                    }.exceptionOrNull()
                    if (failure == null) {
                        onBack()
                    } else {
                        Log.e(TAG, "Failed to delete community $communityId", failure)
                        snackbarHostState.showSnackbar(deleteFailedMsg)
                    }
                }
            }
        )
    }

    groupPendingRemoval?.let { group ->
        val groupName = group.name ?: stringResource(Res.string.unknown)
        ConfirmDialog(
            title = stringResource(Res.string.community_remove_group),
            message = stringResource(Res.string.community_remove_group_confirm, groupName),
            confirmLabel = stringResource(Res.string.community_remove_group),
            dismissLabel = stringResource(Res.string.cancel),
            isDestructive = true,
            onDismiss = { groupPendingRemoval = null },
            onConfirm = {
                groupPendingRemoval = null
                scope.launch {
                    val failure = runCatchingCancellable {
                        communityRepository.removeGroupFromCommunity(communityId, group.conversationId)
                        loadDetail()
                    }.exceptionOrNull()
                    if (failure != null) {
                        Log.e(TAG, "Failed to remove group ${group.conversationId}", failure)
                    }
                    snackbarHostState.showSnackbar(
                        if (failure == null) groupRemovedMsg else groupRemoveFailedMsg
                    )
                }
            }
        )
    }

    MuhabbetScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MuhabbetTopBar(
                title = detail?.name ?: stringResource(Res.string.communities_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back),
                actions = {
                    if (current != null) {
                        if (canManage) {
                            MuhabbetIconButton(
                                icon = Muhabbet.icons.Edit,
                                contentDescription = stringResource(Res.string.community_edit_title),
                                onClick = { showEditDialog = true }
                            )
                        }
                        MuhabbetIconButton(
                            icon = Muhabbet.icons.LeaveGroup,
                            contentDescription = stringResource(Res.string.community_leave_title),
                            onClick = { showLeaveDialog = true }
                        )
                        if (isOwner) {
                            MuhabbetIconButton(
                                icon = Muhabbet.icons.Delete,
                                contentDescription = stringResource(Res.string.community_delete_title),
                                onClick = { showDeleteDialog = true }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
        } else if (detail == null) {
            MuhabbetErrorState(
                message = stringResource(Res.string.error_generic),
                modifier = Modifier.fillMaxSize().padding(padding),
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = { retryKey++ }
            )
        } else {
            val community = detail ?: return@MuhabbetScaffold
            CommunityDetailContent(
                community = community,
                canManage = canManage,
                contentPadding = padding,
                onMembersClick = { onMembersClick(communityId) },
                onAddGroupClick = { showAddGroupSheet = true },
                onGroupClick = onGroupClick,
                onRemoveGroupClick = { groupPendingRemoval = it }
            )
        }
    }
}

private const val TAG = "CommunityDetailScreen"
