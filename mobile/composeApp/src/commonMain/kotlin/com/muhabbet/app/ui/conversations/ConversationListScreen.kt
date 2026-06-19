package com.muhabbet.app.ui.conversations

import com.muhabbet.app.ui.components.ConfirmDialog
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.MediaUploadHelper
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.data.repository.StatusRepository
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.app.platform.rememberImagePickerLauncher
import com.muhabbet.shared.dto.UserStatusGroup
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.shared.model.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.muhabbet.app.ui.theme.MuhabbetSizes
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.model.PresenceStatus
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.app.ui.components.EmptyChatsIllustration
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.normalizeToE164
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val TAG = "ConversationList"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (id: String, name: String, otherUserId: String?, isGroup: Boolean) -> Unit,
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load conversations", e)
            snackbarHostState.showSnackbar(errorMsg)
        }
    }

    // Load on initial + refreshKey changes
    LaunchedEffect(refreshKey, showStatusRow) {
        loadConversations()
        if (showStatusRow) {
            try {
                statusGroups = statusRepository.getContactStatuses()
            } catch (e: Exception) {
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
                        try { wsClient.send(WsMessage.AckMessage(messageId = wsMessage.messageId, conversationId = wsMessage.conversationId, status = MessageStatus.DELIVERED)) } catch (_: Exception) { }
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
                    try {
                        var mediaUrl: String? = null
                        statusPickedImage?.let { img ->
                            val upload = mediaUploadHelper.uploadImage(img.bytes, img.fileName)
                            mediaUrl = upload.url
                        }
                        statusRepository.createStatus(content = text.ifEmpty { null }, mediaUrl = mediaUrl)
                        statusGroups = statusRepository.getContactStatuses()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create status", e)
                    }
                    isUploadingStatus = false
                    showStatusInput = false
                    statusText = ""
                    statusPickedImage = null
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
                } catch (e: Exception) { Log.e(TAG, "Pin toggle failed", e) }
            }
        },
        onArchiveToggle = { conv ->
            showLongPressMenu = false; longPressTargetConv = null
            scope.launch {
                try {
                    if (conv.isArchived) conversationRepository.unarchiveConversation(conv.id)
                    else conversationRepository.archiveConversation(conv.id)
                    loadConversations()
                } catch (e: Exception) { Log.e(TAG, "Archive toggle failed", e) }
            }
        },
        onMuteToggle = { conv ->
            showLongPressMenu = false; longPressTargetConv = null
            if (conv.isMuted) {
                scope.launch {
                    try {
                        conversationRepository.unmuteConversation(conv.id)
                        loadConversations()
                    } catch (e: Exception) { Log.e(TAG, "Unmute failed", e) }
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
                } catch (e: Exception) { Log.e(TAG, "Lock toggle failed", e) }
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
                } catch (e: Exception) { Log.e(TAG, "Mute failed", e) }
            }
            muteTargetConvId = null
        },
        onDismissMute = { showMuteDialog = false; muteTargetConvId = null }
    )

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(stringResource(Res.string.app_name), fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        IconButton(onClick = { isSearching = !isSearching; if (!isSearching) { searchQuery = ""; searchResults = emptyList() } }) {
                            Icon(
                                imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = stringResource(if (isSearching) Res.string.action_close else Res.string.search_messages_placeholder),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(Res.string.settings_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.new_conversation_title),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            if (showTopBar && isSearching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newQuery ->
                        searchQuery = newQuery
                        if (newQuery.length >= 2) {
                            scope.launch {
                                try {
                                    searchResults = messageRepository.searchMessages(newQuery).items
                                } catch (e: Exception) {
                                    Log.e(TAG, "Message search failed", e)
                                    searchResults = emptyList()
                                }
                            }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    placeholder = { Text(stringResource(Res.string.search_messages_placeholder)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small).testTag("search_input")
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
                            } catch (e: Exception) { Log.e(TAG, "Pin toggle failed", e) }
                        }
                    }
                )
            }
        }
    }
}

/**
 * Loading / empty / list body for [ConversationListScreen]. Splits active vs archived,
 * sorts pinned-first, and renders the status row + filter chips above the list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ConversationListBody(
    isLoading: Boolean,
    isRefreshing: Boolean,
    conversations: List<ConversationResponse>,
    activeFilter: ConversationFilter,
    onFilterChange: (ConversationFilter) -> Unit,
    showStatusRow: Boolean,
    statusGroups: List<UserStatusGroup>,
    currentUserId: String,
    contactNameMap: Map<String, String>,
    onlineUsers: Map<String, Boolean>,
    defaultChatName: String,
    onRefresh: () -> Unit,
    onAddStatus: () -> Unit,
    onStatusClick: (userId: String, displayName: String) -> Unit,
    onConversationClick: (id: String, name: String, otherUserId: String?, isGroup: Boolean) -> Unit,
    onConversationLongClick: (ConversationResponse) -> Unit,
    onPin: (ConversationResponse) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        if (isLoading) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(8) {
                    ConversationSkeletonItem()
                    HorizontalDivider()
                }
            }
        } else if (conversations.isEmpty()) {
            EmptyChatsIllustration(
                title = stringResource(Res.string.empty_chats_title),
                subtitle = stringResource(Res.string.empty_chats_subtitle),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Filter conversations
            val filteredConversations = when (activeFilter) {
                ConversationFilter.UNREAD -> conversations.filter { it.unreadCount > 0 }
                ConversationFilter.FAVORITES -> conversations.filter { it.isPinned }
                ConversationFilter.GROUPS -> conversations.filter { it.type == ConversationType.GROUP }
                else -> conversations
            }
            // Split active vs archived
            val activeConversations = filteredConversations.filter { !it.isArchived }
            val archivedConversations = filteredConversations.filter { it.isArchived }

            // Sort: pinned first, then by lastMessageAt
            val sortedConversations = activeConversations.sortedWith(
                compareByDescending<ConversationResponse> { it.isPinned }
                    .thenByDescending { it.lastMessageAt ?: "" }
            )

            LazyColumn {
                if (showStatusRow) {
                    item(key = "status_row") {
                        ConversationStatusRow(
                            statusGroups = statusGroups,
                            conversations = conversations,
                            onAddStatus = onAddStatus,
                            onStatusClick = onStatusClick
                        )
                    }
                }
                item(key = "filter_chips") {
                    ConversationFilterChips(activeFilter = activeFilter, onFilterChange = onFilterChange)
                }
                items(sortedConversations, key = { it.id }) { conv ->
                    ConversationListItemRow(
                        conv = conv,
                        currentUserId = currentUserId,
                        contactNameMap = contactNameMap,
                        onlineUsers = onlineUsers,
                        defaultChatName = defaultChatName,
                        isPinned = conv.isPinned,
                        onConversationClick = onConversationClick,
                        onConversationLongClick = onConversationLongClick,
                        onPin = { onPin(conv) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = MuhabbetSizes.ChatListDividerInset)
                    )
                }

                // Archived section
                if (archivedConversations.isNotEmpty()) {
                    item(key = "archived_header") {
                        Spacer(Modifier.height(MuhabbetSpacing.Medium))
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MuhabbetSpacing.XLarge, vertical = MuhabbetSpacing.Medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.conv_archived_section),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(MuhabbetSpacing.Small))
                            Text(
                                text = "(${archivedConversations.size})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(archivedConversations, key = { "archived_${it.id}" }) { conv ->
                        ConversationListItemRow(
                            conv = conv,
                            currentUserId = currentUserId,
                            contactNameMap = contactNameMap,
                            onlineUsers = onlineUsers,
                            defaultChatName = defaultChatName,
                            isPinned = false,
                            onConversationClick = onConversationClick,
                            onConversationLongClick = onConversationLongClick,
                            onPin = {}
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = MuhabbetSizes.ChatListDividerInset)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Resolves the display name/avatar/online state for a single conversation, then renders a
 * [ConversationItem]. Name priority: contact-saved name > nickname > phone.
 */
@Composable
private fun ConversationListItemRow(
    conv: ConversationResponse,
    currentUserId: String,
    contactNameMap: Map<String, String>,
    onlineUsers: Map<String, Boolean>,
    defaultChatName: String,
    isPinned: Boolean,
    onConversationClick: (id: String, name: String, otherUserId: String?, isGroup: Boolean) -> Unit,
    onConversationLongClick: (ConversationResponse) -> Unit,
    onPin: () -> Unit
) {
    val otherParticipant = conv.participants.firstOrNull { it.userId != currentUserId }
    val isGroup = conv.type == ConversationType.GROUP
    val contactName = if (!isGroup) {
        otherParticipant?.phoneNumber?.let { contactNameMap[it] }
    } else null
    val resolvedName = conv.name
        ?: contactName
        ?: otherParticipant?.displayName
        ?: otherParticipant?.phoneNumber
        ?: defaultChatName
    val isOtherOnline = otherParticipant?.let {
        onlineUsers[it.userId] ?: it.isOnline
    } ?: false
    val avatarUrl = if (isGroup) conv.avatarUrl else otherParticipant?.avatarUrl
    ConversationItem(
        conversation = conv,
        displayName = resolvedName,
        avatarUrl = avatarUrl,
        isOnline = isOtherOnline,
        isGroup = isGroup,
        isPinned = isPinned,
        onClick = { onConversationClick(conv.id, resolvedName, otherParticipant?.userId, isGroup) },
        onLongClick = { onConversationLongClick(conv) },
        onPin = onPin
    )
}

/** Localized labels for the conversation long-press action menu. */
internal data class ConversationActionLabels(
    val pin: String,
    val unpin: String,
    val archive: String,
    val unarchive: String,
    val mute: String,
    val unmute: String,
    val lock: String,
    val unlock: String,
    val delete: String,
    val cancel: String
)

/**
 * Hosts all four dialogs of [ConversationListScreen] (status create, long-press actions,
 * delete confirm, mute picker). Pure wiring — visibility and side effects are driven by the caller.
 */
@Composable
private fun ConversationListDialogs(
    showStatusInput: Boolean,
    statusText: String,
    statusPickedImage: PickedImage?,
    isUploadingStatus: Boolean,
    onStatusTextChange: (String) -> Unit,
    onPickStatusImage: () -> Unit,
    onPostStatus: () -> Unit,
    onDismissStatus: () -> Unit,
    longPressTargetConv: ConversationResponse?,
    actionLabels: ConversationActionLabels,
    onPinToggle: (ConversationResponse) -> Unit,
    onArchiveToggle: (ConversationResponse) -> Unit,
    onMuteToggle: (ConversationResponse) -> Unit,
    onLockToggle: (ConversationResponse) -> Unit,
    onDeleteFromMenu: (ConversationResponse) -> Unit,
    onDismissMenu: () -> Unit,
    deleteTargetConv: ConversationResponse?,
    deleteTitle: String,
    deleteMessage: String,
    onConfirmDelete: (ConversationResponse) -> Unit,
    onDismissDelete: () -> Unit,
    showMuteDialog: Boolean,
    onMuteDuration: (String) -> Unit,
    onDismissMute: () -> Unit
) {
    if (showStatusInput) {
        StatusCreateDialog(
            statusText = statusText,
            pickedImage = statusPickedImage,
            isUploading = isUploadingStatus,
            cancelLabel = actionLabels.cancel,
            onTextChange = onStatusTextChange,
            onPickImage = onPickStatusImage,
            onPost = onPostStatus,
            onDismiss = onDismissStatus
        )
    }

    longPressTargetConv?.let { conv ->
        ConversationActionsDialog(
            conversation = conv,
            pinLabel = actionLabels.pin,
            unpinLabel = actionLabels.unpin,
            archiveLabel = actionLabels.archive,
            unarchiveLabel = actionLabels.unarchive,
            muteLabel = actionLabels.mute,
            unmuteLabel = actionLabels.unmute,
            lockLabel = actionLabels.lock,
            unlockLabel = actionLabels.unlock,
            deleteLabel = actionLabels.delete,
            cancelLabel = actionLabels.cancel,
            onPinToggle = { onPinToggle(conv) },
            onArchiveToggle = { onArchiveToggle(conv) },
            onMuteToggle = { onMuteToggle(conv) },
            onLockToggle = { onLockToggle(conv) },
            onDelete = { onDeleteFromMenu(conv) },
            onDismiss = onDismissMenu
        )
    }

    deleteTargetConv?.let { conv ->
        ConfirmDialog(
            title = deleteTitle,
            message = deleteMessage,
            confirmLabel = actionLabels.delete,
            onConfirm = { onConfirmDelete(conv) },
            onDismiss = onDismissDelete,
            isDestructive = true,
            dismissLabel = actionLabels.cancel
        )
    }

    if (showMuteDialog) {
        MutePickerDialog(
            onDismiss = onDismissMute,
            onMuteDuration = onMuteDuration
        )
    }
}
