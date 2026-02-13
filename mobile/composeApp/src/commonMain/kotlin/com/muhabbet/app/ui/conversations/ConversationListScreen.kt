package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.data.repository.StatusRepository
import com.muhabbet.shared.dto.UserStatusGroup
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.shared.model.Message
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.heightIn
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.PresenceStatus
import com.muhabbet.shared.protocol.WsMessage
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.muhabbet.app.ui.components.EmptyChatsIllustration
import com.muhabbet.app.util.normalizeToE164
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ConversationListScreen(
    onConversationClick: (id: String, name: String, otherUserId: String?, isGroup: Boolean) -> Unit,
    onNewConversation: () -> Unit,
    onSettings: () -> Unit,
    refreshKey: Int = 0,
    conversationRepository: ConversationRepository = koinInject(),
    messageRepository: MessageRepository = koinInject(),
    wsClient: WsClient = koinInject(),
    tokenStorage: TokenStorage = koinInject(),
    contactsProvider: ContactsProvider = koinInject(),
    statusRepository: StatusRepository = koinInject()
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

    // Filter state: "all", "unread", "groups", "channels"
    var activeFilter by remember { mutableStateOf("all") }

    // Status/Stories state
    var statusGroups by remember { mutableStateOf<List<UserStatusGroup>>(emptyList()) }
    var showStatusInput by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    val defaultChatName = stringResource(Res.string.chat_default_name)
    val errorMsg = stringResource(Res.string.error_load_conversations)
    val convDeleteTitle = stringResource(Res.string.conv_delete_title)
    val convDeleteConfirm = stringResource(Res.string.conv_delete_confirm)
    val convDeleteFailed = stringResource(Res.string.conv_delete_failed)
    val cancelText = stringResource(Res.string.cancel)
    val deleteText = stringResource(Res.string.delete)

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
            snackbarHostState.showSnackbar(errorMsg)
        }
    }

    // Load on initial + refreshKey changes
    LaunchedEffect(refreshKey) {
        loadConversations()
        try { statusGroups = statusRepository.getContactStatuses() } catch (_: Exception) { }
        isLoading = false
    }

    // Auto-refresh on incoming WS messages + presence updates
    LaunchedEffect(Unit) {
        wsClient.incoming.collect { wsMessage ->
            when (wsMessage) {
                is WsMessage.NewMessage, is WsMessage.StatusUpdate -> {
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
            } catch (_: Exception) { }
        }
    }

    // Status creation dialog
    if (showStatusInput) {
        AlertDialog(
            onDismissRequest = { showStatusInput = false; statusText = "" },
            title = { Text(stringResource(Res.string.status_create_title)) },
            text = {
                OutlinedTextField(
                    value = statusText,
                    onValueChange = { statusText = it },
                    placeholder = { Text(stringResource(Res.string.status_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = statusText.trim()
                        if (text.isNotEmpty()) {
                            showStatusInput = false
                            statusText = ""
                            scope.launch {
                                try {
                                    statusRepository.createStatus(content = text, mediaUrl = null)
                                    statusGroups = statusRepository.getContactStatuses()
                                } catch (_: Exception) { }
                            }
                        }
                    },
                    enabled = statusText.isNotBlank()
                ) { Text(stringResource(Res.string.status_post)) }
            },
            dismissButton = {
                TextButton(onClick = { showStatusInput = false; statusText = "" }) {
                    Text(cancelText)
                }
            }
        )
    }

    // Delete conversation dialog
    if (showDeleteDialog && deleteTargetConv != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deleteTargetConv = null },
            title = { Text(convDeleteTitle) },
            text = { Text(convDeleteConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    val conv = deleteTargetConv!!
                    showDeleteDialog = false
                    deleteTargetConv = null
                    scope.launch {
                        try {
                            conversationRepository.deleteConversation(conv.id)
                            conversations = conversations.filter { it.id != conv.id }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(convDeleteFailed)
                        }
                    }
                }) { Text(deleteText, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteTargetConv = null }) {
                    Text(cancelText)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { isSearching = !isSearching; if (!isSearching) { searchQuery = ""; searchResults = emptyList() } }) {
                        Icon(
                            imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewConversation,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            if (isSearching) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newQuery ->
                        searchQuery = newQuery
                        if (newQuery.length >= 2) {
                            scope.launch {
                                try {
                                    searchResults = messageRepository.searchMessages(newQuery).items
                                } catch (_: Exception) { searchResults = emptyList() }
                            }
                        } else {
                            searchResults = emptyList()
                        }
                    },
                    placeholder = { Text(stringResource(Res.string.search_messages_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Search results
            if (isSearching && searchResults.isNotEmpty()) {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(searchResults, key = { it.id }) { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val conv = conversations.firstOrNull { it.id == msg.conversationId }
                                    val otherP = conv?.participants?.firstOrNull { it.userId != currentUserId }
                                    val name = conv?.name ?: otherP?.displayName ?: otherP?.phoneNumber ?: ""
                                    onConversationClick(msg.conversationId, name, otherP?.userId, conv?.type == ConversationType.GROUP)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = msg.content.take(80),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2
                                )
                                Text(
                                    text = formatTimestamp(msg.serverTimestamp?.toString() ?: ""),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            } else {

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadConversations()
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                    "unread" -> conversations.filter { it.unreadCount > 0 }
                    "groups" -> conversations.filter { it.type == ConversationType.GROUP }
                    else -> conversations
                }
                // Sort: pinned first, then by lastMessageAt
                val sortedConversations = filteredConversations.sortedWith(
                    compareByDescending<ConversationResponse> { it.isPinned }
                        .thenByDescending { it.lastMessageAt ?: "" }
                )

                LazyColumn {
                    // Status row
                    item(key = "status_row") {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                        ) {
                            // "Add status" button
                            item(key = "add_status") {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { showStatusInput = true }.width(64.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(56.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(Res.string.status_my),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // Contact statuses
                            items(
                                count = statusGroups.size,
                                key = { statusGroups[it].userId }
                            ) { index ->
                                val group = statusGroups[index]
                                val conv = conversations.flatMap { it.participants }
                                    .firstOrNull { it.userId == group.userId }
                                val displayName = conv?.displayName ?: conv?.phoneNumber ?: group.userId.take(8)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(64.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    ) {
                                        com.muhabbet.app.ui.components.UserAvatar(
                                            avatarUrl = conv?.avatarUrl,
                                            displayName = displayName,
                                            size = 56.dp
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (statusGroups.isNotEmpty() || true) {
                            HorizontalDivider()
                        }
                    }
                    // Filter chips
                    item(key = "filter_chips") {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = activeFilter == "all",
                                    onClick = { activeFilter = "all" },
                                    label = { Text(stringResource(Res.string.filter_all)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = activeFilter == "unread",
                                    onClick = { activeFilter = if (activeFilter == "unread") "all" else "unread" },
                                    label = { Text(stringResource(Res.string.filter_unread)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                            item {
                                FilterChip(
                                    selected = activeFilter == "groups",
                                    onClick = { activeFilter = if (activeFilter == "groups") "all" else "groups" },
                                    label = { Text(stringResource(Res.string.filter_groups)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                    }
                    items(sortedConversations, key = { it.id }) { conv ->
                        val otherParticipant = conv.participants
                            .firstOrNull { it.userId != currentUserId }
                        val isGroup = conv.type == ConversationType.GROUP
                        // Name priority: 1-Contact saved name, 2-Nickname, 3-Phone
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
                            isPinned = conv.isPinned,
                            onClick = { onConversationClick(conv.id, resolvedName, otherParticipant?.userId, isGroup) },
                            onLongClick = {
                                deleteTargetConv = conv
                                showDeleteDialog = true
                            },
                            onPin = {
                                scope.launch {
                                    try {
                                        if (conv.isPinned) {
                                            conversationRepository.unpinConversation(conv.id)
                                        } else {
                                            conversationRepository.pinConversation(conv.id)
                                        }
                                        loadConversations()
                                    } catch (_: Exception) { }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
            } // end else (non-search)
        } // end Column
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: ConversationResponse,
    displayName: String,
    avatarUrl: String? = null,
    isOnline: Boolean,
    isGroup: Boolean = false,
    isPinned: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onPin: () -> Unit = {}
) {
    val hasUnread = conversation.unreadCount > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with online indicator
        Box {
            com.muhabbet.app.ui.components.UserAvatar(
                avatarUrl = avatarUrl,
                displayName = displayName,
                size = 48.dp,
                isGroup = isGroup
            )
            // Green online dot
            if (isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = 1.dp, y = 1.dp)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(Color(0xFF4CAF50), CircleShape)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPinned) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (conversation.lastMessagePreview != null) {
                Text(
                    text = conversation.lastMessagePreview!!,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (hasUnread) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (hasUnread) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            if (conversation.lastMessageAt != null) {
                Text(
                    text = formatTimestamp(conversation.lastMessageAt!!),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (hasUnread) {
                Spacer(Modifier.height(4.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(conversation.unreadCount.toString())
                }
            }
        }
    }
}

private fun firstGrapheme(text: String): String =
    com.muhabbet.app.ui.profile.firstGrapheme(text)

private fun formatTimestamp(timestamp: String): String {
    return try {
        val instant = kotlinx.datetime.Instant.parse(timestamp)
        val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
        val msgDate = instant.toLocalDateTime(tz)
        val nowDate = kotlinx.datetime.Clock.System.now().toLocalDateTime(tz)

        if (msgDate.date == nowDate.date) {
            // Today — show HH:mm
            "${msgDate.hour.toString().padStart(2, '0')}:${msgDate.minute.toString().padStart(2, '0')}"
        } else if (msgDate.year == nowDate.year) {
            // Same year — show dd.MM
            "${msgDate.dayOfMonth.toString().padStart(2, '0')}.${msgDate.monthNumber.toString().padStart(2, '0')}"
        } else {
            // Different year — show dd.MM.yy
            "${msgDate.dayOfMonth.toString().padStart(2, '0')}.${msgDate.monthNumber.toString().padStart(2, '0')}.${msgDate.year % 100}"
        }
    } catch (_: Exception) {
        ""
    }
}
