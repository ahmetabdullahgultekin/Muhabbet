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
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.platform.ContactsProvider
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType
import com.muhabbet.shared.model.PresenceStatus
import com.muhabbet.shared.protocol.WsMessage
import androidx.compose.material.icons.filled.Group
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
    wsClient: WsClient = koinInject(),
    tokenStorage: TokenStorage = koinInject(),
    contactsProvider: ContactsProvider = koinInject()
) {
    var conversations by remember { mutableStateOf<List<ConversationResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }
    val scope = rememberCoroutineScope()

    // Track online status by userId (updated by PresenceUpdate messages)
    val onlineUsers = remember { mutableStateMapOf<String, Boolean>() }

    // Map of normalized E.164 phone → device contact saved name
    val contactNameMap = remember { mutableStateMapOf<String, String>() }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTargetConv by remember { mutableStateOf<ConversationResponse?>(null) }

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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadConversations()
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            } else if (conversations.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(Res.string.conversations_empty_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.conversations_empty_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(conversations, key = { it.id }) { conv ->
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
                            onClick = { onConversationClick(conv.id, resolvedName, otherParticipant?.userId, isGroup) },
                            onLongClick = {
                                deleteTargetConv = conv
                                showDeleteDialog = true
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
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
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
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
            Text(
                text = displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (conversation.lastMessagePreview != null) {
                Text(
                    text = conversation.lastMessagePreview!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (conversation.unreadCount > 0) {
                Spacer(Modifier.height(4.dp))
                Badge { Text(conversation.unreadCount.toString()) }
            }
        }
    }
}

private fun firstGrapheme(text: String): String =
    com.muhabbet.app.ui.profile.firstGrapheme(text)

private fun normalizeToE164(phone: String): String? {
    val digits = phone.removePrefix("+")
    return when {
        phone.startsWith("+90") && digits.length == 12 -> phone
        digits.startsWith("90") && digits.length == 12 -> "+$digits"
        digits.startsWith("0") && digits.length == 11 -> "+90${digits.drop(1)}"
        digits.startsWith("5") && digits.length == 10 -> "+90$digits"
        phone.startsWith("+") && digits.length >= 10 -> phone
        else -> null
    }
}

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
