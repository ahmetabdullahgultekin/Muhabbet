package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.designsystem.components.ConfirmDialog
import com.muhabbet.designsystem.components.MuhabbetEmptyState
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val TAG = "ArchivedChats"

/**
 * Every conversation currently archived, reached from the [ArchivedChatsRow] pinned above the main
 * list (#612). The row is the discovery mechanism; this screen is where "where did it go" resolves —
 * the same long-press menu the main list uses (pin/archive/mute/lock/delete) works here unchanged,
 * so unarchiving a chat is never a different gesture from the one that put it here.
 *
 * A separate screen rather than an expanding section in the main list: every other secondary view of
 * "some conversations, filtered" in this app — starred messages, shared media, call history — is its
 * own screen reached by pushing onto [com.muhabbet.app.navigation.MainComponent]'s stack, not an
 * inline expansion of [ConversationListBody]. That composable already sits close to the 300-line
 * guideline; folding archived-mode state into it would grow it rather than reuse it, for a feature
 * that is visited far less often than the list it would permanently complicate.
 *
 * Deliberately minimal compared to the main list: no contact-name resolution, no live presence
 * tracking, no status row, no search — the same trade [StarredMessagesScreen] already makes for a
 * secondary view of the conversation list. Unarchiving is one tap away in the long-press menu either
 * way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedChatsScreen(
    onBack: () -> Unit,
    onConversationClick: (ChatTarget) -> Unit,
    conversationRepository: ConversationRepository = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var conversations by remember { mutableStateOf<List<ConversationResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showLongPressMenu by remember { mutableStateOf(false) }
    var longPressTargetConv by remember { mutableStateOf<ConversationResponse?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTargetConv by remember { mutableStateOf<ConversationResponse?>(null) }
    var showMuteDialog by remember { mutableStateOf(false) }
    var muteTargetConvId by remember { mutableStateOf<String?>(null) }

    val defaultChatName = stringResource(Res.string.chat_default_name)
    val errorMsg = stringResource(Res.string.error_load_conversations)
    val actionFailedMsg = stringResource(Res.string.error_action_failed)
    val convDeleteTitle = stringResource(Res.string.conv_delete_title)
    val convDeleteConfirm = stringResource(Res.string.conv_delete_confirm)
    val convDeleteFailed = stringResource(Res.string.conv_delete_failed)
    val cancelText = stringResource(Res.string.cancel)
    val deleteText = stringResource(Res.string.delete)
    val pinText = stringResource(Res.string.conv_pin)
    val unpinText = stringResource(Res.string.conv_unpin)
    val archiveText = stringResource(Res.string.conv_archive)
    val unarchiveText = stringResource(Res.string.conv_unarchive)
    val muteText = stringResource(Res.string.conv_mute)
    val unmuteText = stringResource(Res.string.conv_unmute)
    val lockText = stringResource(Res.string.chat_lock)
    val unlockText = stringResource(Res.string.chat_unlock)

    suspend fun load() {
        runCatchingCancellable {
            val result = conversationRepository.getConversations()
            conversations = result.items.filter { it.isArchived }
        }.onFailure { e ->
            Log.e(TAG, "Failed to load archived conversations", e)
            snackbarHostState.showSnackbar(errorMsg)
        }
    }

    LaunchedEffect(Unit) {
        load()
        isLoading = false
    }

    longPressTargetConv?.takeIf { showLongPressMenu }?.let { conv ->
        ConversationActionsDialog(
            conversation = conv,
            pinLabel = pinText,
            unpinLabel = unpinText,
            archiveLabel = archiveText,
            unarchiveLabel = unarchiveText,
            muteLabel = muteText,
            unmuteLabel = unmuteText,
            lockLabel = lockText,
            unlockLabel = unlockText,
            deleteLabel = deleteText,
            cancelLabel = cancelText,
            onPinToggle = {
                showLongPressMenu = false; longPressTargetConv = null
                scope.launch {
                    runCatchingCancellable {
                        if (conv.isPinned) conversationRepository.unpinConversation(conv.id)
                        else conversationRepository.pinConversation(conv.id)
                        load()
                    }.onFailure { e ->
                        Log.e(TAG, "Pin toggle failed", e)
                        snackbarHostState.showSnackbar(actionFailedMsg)
                    }
                }
            },
            onArchiveToggle = {
                // Every conversation on this screen is already archived, so this is always an
                // unarchive — the mechanism #612 asked for. It disappears from this list on reload
                // because it no longer matches the filter that populated it.
                showLongPressMenu = false; longPressTargetConv = null
                scope.launch {
                    runCatchingCancellable {
                        conversationRepository.unarchiveConversation(conv.id)
                        load()
                    }.onFailure { e ->
                        Log.e(TAG, "Unarchive failed", e)
                        snackbarHostState.showSnackbar(actionFailedMsg)
                    }
                }
            },
            onMuteToggle = {
                showLongPressMenu = false; longPressTargetConv = null
                if (conv.isMuted) {
                    scope.launch {
                        runCatchingCancellable {
                            conversationRepository.unmuteConversation(conv.id)
                            load()
                        }.onFailure { e ->
                            Log.e(TAG, "Unmute failed", e)
                            snackbarHostState.showSnackbar(actionFailedMsg)
                        }
                    }
                } else {
                    muteTargetConvId = conv.id
                    showMuteDialog = true
                }
            },
            onLockToggle = {
                showLongPressMenu = false; longPressTargetConv = null
                scope.launch {
                    runCatchingCancellable {
                        if (conv.isLocked) conversationRepository.unlockConversation(conv.id)
                        else conversationRepository.lockConversation(conv.id)
                        load()
                    }.onFailure { e ->
                        Log.e(TAG, "Lock toggle failed", e)
                        snackbarHostState.showSnackbar(actionFailedMsg)
                    }
                }
            },
            onDelete = {
                showLongPressMenu = false; deleteTargetConv = conv; longPressTargetConv = null; showDeleteDialog = true
            },
            onDismiss = { showLongPressMenu = false; longPressTargetConv = null }
        )
    }

    deleteTargetConv?.takeIf { showDeleteDialog }?.let { conv ->
        ConfirmDialog(
            title = convDeleteTitle,
            message = convDeleteConfirm,
            confirmLabel = deleteText,
            dismissLabel = cancelText,
            isDestructive = true,
            onConfirm = {
                showDeleteDialog = false; deleteTargetConv = null
                scope.launch {
                    runCatchingCancellable {
                        conversationRepository.deleteConversation(conv.id)
                        conversations = conversations.filter { it.id != conv.id }
                    }.onFailure { e ->
                        Log.e(TAG, "Failed to delete conversation", e)
                        snackbarHostState.showSnackbar(convDeleteFailed)
                    }
                }
            },
            onDismiss = { showDeleteDialog = false; deleteTargetConv = null }
        )
    }

    if (showMuteDialog && muteTargetConvId != null) {
        MutePickerDialog(
            onDismiss = { showMuteDialog = false; muteTargetConvId = null },
            onMuteDuration = { duration ->
                val convId = muteTargetConvId
                showMuteDialog = false; muteTargetConvId = null
                if (convId != null) {
                    scope.launch {
                        runCatchingCancellable {
                            conversationRepository.muteConversation(convId, duration)
                            load()
                        }.onFailure { e ->
                            Log.e(TAG, "Mute failed", e)
                            snackbarHostState.showSnackbar(actionFailedMsg)
                        }
                    }
                }
            }
        )
    }

    MuhabbetScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.conv_archived_section),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        }
    ) { padding ->
        when {
            isLoading -> MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
            conversations.isEmpty() -> MuhabbetEmptyState(
                title = stringResource(Res.string.archived_empty),
                icon = Muhabbet.icons.Archive,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(conversations, key = { it.id }) { conv ->
                    // isPinned forced false and onPin a no-op, matching the treatment the old
                    // bottom-of-list archived section gave these rows: pin state is still visible and
                    // toggleable through the long-press menu below, which reads conv.isPinned
                    // directly rather than the value handed to the row.
                    ArchivedConversationRow(
                        conv = conv,
                        currentUserId = currentUserId,
                        defaultChatName = defaultChatName,
                        onConversationClick = onConversationClick,
                        onConversationLongClick = { longPressTargetConv = it; showLongPressMenu = true }
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
 * Thin wrapper so this file does not need [ConversationListItemRow] to widen its visibility beyond
 * `private` in `ConversationListBody.kt` — both files are in the same package, but the row it wraps,
 * [ConversationItem], is what actually needs reuse here, via the same resolution rule (contact name >
 * nickname > phone) every other conversation row in the app uses.
 */
@Composable
private fun ArchivedConversationRow(
    conv: ConversationResponse,
    currentUserId: String,
    defaultChatName: String,
    onConversationClick: (ChatTarget) -> Unit,
    onConversationLongClick: (ConversationResponse) -> Unit
) {
    val otherParticipant = conv.participants.firstOrNull { it.userId != currentUserId }
    val target = conv.toChatTarget(currentUserId, defaultChatName)
    val isOtherOnline = otherParticipant?.isOnline ?: false
    ConversationItem(
        conversation = conv,
        displayName = target.name,
        avatarUrl = target.avatarUrl,
        isOnline = isOtherOnline,
        isGroup = target.isGroup,
        isPinned = false,
        onClick = { onConversationClick(target) },
        onLongClick = { onConversationLongClick(conv) },
        onPin = {}
    )
}
