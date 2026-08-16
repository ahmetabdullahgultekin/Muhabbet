package com.muhabbet.app.ui.conversations

import com.muhabbet.designsystem.components.MuhabbetTopBar
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.ui.connection.ConnectionStrip
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.MediaUploadHelper
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.data.repository.StatusRepository
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.app.platform.rememberImagePickerLauncher
import com.muhabbet.shared.dto.UserStatusGroup
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.shared.model.Message
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.model.PresenceStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.app.util.normalizeToE164
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTextField
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.muhabbet.designsystem.components.MuhabbetIconButton

private const val TAG = "ConversationList"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (ChatTarget) -> Unit,
    onNewConversation: () -> Unit,
    onSettings: () -> Unit,
    onStatusClick: (userId: String, displayName: String) -> Unit = { _, _ -> },
    refreshKey: Int = 0,
    showTopBar: Boolean = true,
    showStatusRow: Boolean = true,
    conversationRepository: ConversationRepository = koinInject(),
    messageRepository: MessageRepository = koinInject(),
    wsClient: WsClient = koinInject(),
    tokenStorage: TokenStorage = koinInject(),
    contactsProvider: ContactsProvider = koinInject(),
    statusRepository: StatusRepository = koinInject(),
    mediaUploadHelper: MediaUploadHelper = koinInject()
) {
    var conversations by remember { mutableStateOf<List<ConversationResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Message>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }
    val scope = rememberCoroutineScope()
    // Rendered by the ConnectionStrip below. Nothing had ever read this flow before #511.
    val connectionState by wsClient.connectionState.collectAsState()

    // Track online status by userId (updated by PresenceUpdate messages)
    val onlineUsers = remember { mutableStateMapOf<String, Boolean>() }

    // Map of normalized E.164 phone → device contact saved name
    val contactNameMap = remember { mutableStateMapOf<String, String>() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTargetConv by remember { mutableStateOf<ConversationResponse?>(null) }
    var showLongPressMenu by remember { mutableStateOf(false) }
    var longPressTargetConv by remember { mutableStateOf<ConversationResponse?>(null) }

    // Filter state
    var activeFilter by remember { mutableStateOf(ConversationFilter.ALL) }

    // Status/Stories state
    var statusGroups by remember { mutableStateOf<List<UserStatusGroup>>(emptyList()) }
    var showStatusInput by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var statusPickedImage by remember { mutableStateOf<PickedImage?>(null) }
    var isUploadingStatus by remember { mutableStateOf(false) }
    val statusImagePicker = rememberImagePickerLauncher { image ->
        statusPickedImage = image
    }

    val defaultChatName = stringResource(Res.string.chat_default_name)
    val errorMsg = stringResource(Res.string.error_load_conversations)
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
    val actionFailedMsg = stringResource(Res.string.error_action_failed)
    val searchFailedMsg = stringResource(Res.string.search_failed)
    val statusPostFailedMsg = stringResource(Res.string.status_post_failed)

    var showMuteDialog by remember { mutableStateOf(false) }
    var muteTargetConvId by remember { mutableStateOf<String?>(null) }

    suspend fun loadConversations() {
        try {
            val result = conversationRepository.getConversations()
            conversations = result.items
            // Initialize online status from server response
            result.items.forEach { conv ->
                conv.participants.forEach { p ->
                    if (p.userId != currentUserId) {
                        onlineUsers[p.userId] = p.isOnline
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load conversations", e)
            // Reported on a separate coroutine on purpose: showSnackbar suspends until the snackbar
            // is dismissed (~4s), and loadConversations() is also driven by the WebSocket collector
            // and by pull-to-refresh. Awaiting it here would stall the collector for every incoming
            // event and hold the refresh spinner for the life of the snackbar.
            scope.launch { snackbarHostState.showSnackbar(errorMsg) }
        }
    }

    // Load on initial + refreshKey changes
    LaunchedEffect(refreshKey, showStatusRow) {
        loadConversations()
        if (showStatusRow) {
            // Deliberately absorbed. The status row is an optional strip above a conversation list
            // the user came here for; a second snackbar stacked behind the conversation one would
            // report the same outage twice. The log is the record.
            runCatchingCancellable { statusGroups = statusRepository.getContactStatuses() }
                .onFailure { e ->
                    Log.e(TAG, "Failed to load contact statuses", e)
                    statusGroups = emptyList()
                }
        } else {
            statusGroups = emptyList()
        }
        isLoading = false
    }

    // Auto-refresh on incoming WS messages + presence updates
    LaunchedEffect(Unit) {
        wsClient.incoming.collect { wsMessage ->
            when (wsMessage) {
                is WsMessage.NewMessage -> {
                    if (wsMessage.senderId != currentUserId) {
                        // sendAck() cannot kill this collector — a dropped socket queues the receipt
                        // for the next connect instead of throwing (#478), and this collector also
                        // drives the list auto-refresh, so killing it would freeze the whole list.
                        // Cancellation still propagates, so a collector being torn down does not log
                        // a phantom failure.
                        val sentNow = wsClient.sendAck(WsMessage.AckMessage(messageId = wsMessage.messageId, conversationId = wsMessage.conversationId, status = MessageStatus.DELIVERED))
                        if (!sentNow) Log.d(TAG, "DELIVERED receipt for ${wsMessage.messageId} queued for the next reconnect")
                    }
                    loadConversations()
                }
                is WsMessage.StatusUpdate -> {
                    loadConversations()
                }
                is WsMessage.PresenceUpdate -> {
                    if (wsMessage.conversationId == null && wsMessage.userId != currentUserId) {
                        onlineUsers[wsMessage.userId] = wsMessage.status == PresenceStatus.ONLINE
                    }
                }
                is WsMessage.GroupMemberAdded,
                is WsMessage.GroupMemberRemoved,
                is WsMessage.GroupInfoUpdated,
                is WsMessage.GroupRoleUpdated,
                is WsMessage.GroupMemberLeft,
                is WsMessage.MessageDeleted,
                is WsMessage.MessageEdited -> {
                    loadConversations()
                }
                else -> {}
            }
        }
    }

    // Load device contacts for name resolution (contact name > nickname > phone)
    LaunchedEffect(Unit) {
        if (contactsProvider.hasPermission()) {
            try {
                val deviceContacts = withContext(Dispatchers.Default) {
                    contactsProvider.readContacts()
                }
                deviceContacts.forEach { contact ->
                    val digits = contact.phoneNumber.filter { c -> c.isDigit() || c == '+' }
                    val normalized = normalizeToE164(digits)
                    if (normalized != null) {
                        contactNameMap[normalized] = contact.name
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read device contacts", e)
            }
        }
    }

    val actionLabels = ConversationActionLabels(
        pin = pinText, unpin = unpinText, archive = archiveText, unarchive = unarchiveText,
        mute = muteText, unmute = unmuteText, lock = lockText, unlock = unlockText,
        delete = deleteText, cancel = cancelText
    )

    ConversationListDialogs(
        showStatusInput = showStatusInput,
        statusText = statusText,
        statusPickedImage = statusPickedImage,
        isUploadingStatus = isUploadingStatus,
        onStatusTextChange = { statusText = it },
        onPickStatusImage = { statusImagePicker.launch() },
        onPostStatus = {
            val text = statusText.trim()
            if (text.isNotEmpty() || statusPickedImage != null) {
                isUploadingStatus = true
                scope.launch {
                    var postFailed = false
                    try {
                        var mediaUrl: String? = null
                        statusPickedImage?.let { img ->
                            val upload = mediaUploadHelper.uploadImage(img.bytes, img.fileName)
                            mediaUrl = upload.url
                        }
                        statusRepository.createStatus(content = text.ifEmpty { null }, mediaUrl = mediaUrl)
                        statusGroups = statusRepository.getContactStatuses()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // The composer closes either way, so without this the status just never
                        // appears and the user has no reason to suspect it did not post.
                        Log.e(TAG, "Failed to create status", e)
                        postFailed = true
                    }
                    isUploadingStatus = false
                    showStatusInput = false
                    statusText = ""
                    statusPickedImage = null
                    if (postFailed) snackbarHostState.showSnackbar(statusPostFailedMsg)
                }
            }
        },
        onDismissStatus = { showStatusInput = false; statusText = ""; statusPickedImage = null },
        longPressTargetConv = if (showLongPressMenu) longPressTargetConv else null,
        actionLabels = actionLabels,
        onPinToggle = { conv ->
            showLongPressMenu = false; longPressTargetConv = null
            scope.launch {
                try {
                    if (conv.isPinned) conversationRepository.unpinConversation(conv.id)
                    else conversationRepository.pinConversation(conv.id)
                    loadConversations()
                } catch (e: Exception) {
                    // Unchecked post/delete until now: a rejected pin silently left the row
                    // unchanged and the tap looked like it had simply not registered.
                    Log.e(TAG, "Pin toggle failed", e)
                    snackbarHostState.showSnackbar(actionFailedMsg)
                }
            }
        },
        onArchiveToggle = { conv ->
            showLongPressMenu = false; longPressTargetConv = null
            scope.launch {
                try {
                    if (conv.isArchived) conversationRepository.unarchiveConversation(conv.id)
                    else conversationRepository.archiveConversation(conv.id)
                    loadConversations()
                } catch (e: Exception) {
                    Log.e(TAG, "Archive toggle failed", e)
                    snackbarHostState.showSnackbar(actionFailedMsg)
                }
            }
        },
        onMuteToggle = { conv ->
            showLongPressMenu = false; longPressTargetConv = null
            if (conv.isMuted) {
                scope.launch {
                    try {
                        conversationRepository.unmuteConversation(conv.id)
                        loadConversations()
                    } catch (e: Exception) {
                        Log.e(TAG, "Unmute failed", e)
                        snackbarHostState.showSnackbar(actionFailedMsg)
                    }
                }
            } else {
                muteTargetConvId = conv.id
                showMuteDialog = true
            }
        },
        onLockToggle = { conv ->
            showLongPressMenu = false; longPressTargetConv = null
            scope.launch {
                try {
                    if (conv.isLocked) conversationRepository.unlockConversation(conv.id)
                    else conversationRepository.lockConversation(conv.id)
                    loadConversations()
                } catch (e: Exception) {
                    Log.e(TAG, "Lock toggle failed", e)
                    snackbarHostState.showSnackbar(actionFailedMsg)
                }
            }
        },
        onDeleteFromMenu = { conv ->
            showLongPressMenu = false; deleteTargetConv = conv; longPressTargetConv = null; showDeleteDialog = true
        },
        onDismissMenu = { showLongPressMenu = false; longPressTargetConv = null },
        deleteTargetConv = if (showDeleteDialog) deleteTargetConv else null,
        deleteTitle = convDeleteTitle,
        deleteMessage = convDeleteConfirm,
        onConfirmDelete = { conv ->
            showDeleteDialog = false; deleteTargetConv = null
            scope.launch {
                try {
                    conversationRepository.deleteConversation(conv.id)
                    conversations = conversations.filter { it.id != conv.id }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete conversation", e)
                    snackbarHostState.showSnackbar(convDeleteFailed)
                }
            }
        },
        onDismissDelete = { showDeleteDialog = false; deleteTargetConv = null },
        showMuteDialog = showMuteDialog && muteTargetConvId != null,
        onMuteDuration = { duration ->
            val convId = muteTargetConvId ?: return@ConversationListDialogs
            scope.launch {
                try {
                    conversationRepository.muteConversation(convId, duration)
                    loadConversations()
                } catch (e: Exception) {
                    Log.e(TAG, "Mute failed", e)
                    snackbarHostState.showSnackbar(actionFailedMsg)
                }
            }
            muteTargetConvId = null
        },
        onDismissMute = { showMuteDialog = false; muteTargetConvId = null }
    )

    MuhabbetScaffold(
        topBar = {
            if (showTopBar) {
                MuhabbetTopBar(
                    title = stringResource(Res.string.app_name),
                    actions = {
                        MuhabbetIconButton(
                            icon = if (isSearching) Muhabbet.icons.Close else Muhabbet.icons.Search,
                            contentDescription = stringResource(if (isSearching) Res.string.action_close else Res.string.search_messages_placeholder),
                            onClick = { isSearching = !isSearching; if (!isSearching) { searchQuery = ""; searchResults = emptyList() } },
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        MuhabbetIconButton(
                            icon = Muhabbet.icons.Settings,
                            contentDescription = stringResource(Res.string.settings_title),
                            onClick = onSettings,
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewConversation,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("new_chat_fab")
            ) {
                Icon(
                    imageVector = Muhabbet.icons.Add,
                    contentDescription = stringResource(Res.string.new_conversation_title),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Above the search field and the list, outside the pull-to-refresh area so it cannot
            // scroll away or fight the refresh gesture. Self-hiding — see ConnectionStrip.
            ConnectionStrip(state = connectionState)
            // Search bar
            if (showTopBar && isSearching) {
                MuhabbetTextField(
                    value = searchQuery,
                    onValueChange = { newQuery ->
                        searchQuery = newQuery
                        if (newQuery.length >= 2) {
                            scope.launch {
                                runCatchingCancellable {
                                    searchResults = messageRepository.searchMessages(newQuery).items
                                }.onFailure { e ->
                                    // An empty result list is how "no matches" renders, so a failed
                                    // search that only cleared the list read as a confident "nothing
                                    // in your messages says that".
                                    Log.e(TAG, "Message search failed", e)
                                    searchResults = emptyList()
                                    snackbarHostState.showSnackbar(searchFailedMsg)
                                }
                            }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small).testTag("search_input"),
                    placeholder = stringResource(Res.string.search_messages_placeholder),
                    singleLine = true,
                    imeAction = ImeAction.Search
                )
            }

            // Search results
            if (isSearching && searchResults.isNotEmpty()) {
                MessageSearchResults(
                    results = searchResults,
                    conversations = conversations,
                    currentUserId = currentUserId,
                    modifier = Modifier.weight(1f),
                    onResultClick = onConversationClick
                )
            } else {
                ConversationListBody(
                    isLoading = isLoading,
                    isRefreshing = isRefreshing,
                    conversations = conversations,
                    activeFilter = activeFilter,
                    onFilterChange = { activeFilter = it },
                    showStatusRow = showStatusRow,
                    statusGroups = statusGroups,
                    currentUserId = currentUserId,
                    contactNameMap = contactNameMap,
                    onlineUsers = onlineUsers,
                    defaultChatName = defaultChatName,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            loadConversations()
                            isRefreshing = false
                        }
                    },
                    onAddStatus = { showStatusInput = true },
                    onStatusClick = onStatusClick,
                    onConversationClick = onConversationClick,
                    onConversationLongClick = { conv ->
                        longPressTargetConv = conv
                        showLongPressMenu = true
                    },
                    onPin = { conv ->
                        scope.launch {
                            try {
                                if (conv.isPinned) conversationRepository.unpinConversation(conv.id)
                                else conversationRepository.pinConversation(conv.id)
                                loadConversations()
                            } catch (e: Exception) {
                    // Unchecked post/delete until now: a rejected pin silently left the row
                    // unchanged and the tap looked like it had simply not registered.
                    Log.e(TAG, "Pin toggle failed", e)
                    snackbarHostState.showSnackbar(actionFailedMsg)
                }
                        }
                    }
                )
            }
        }
    }
}
