package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.platform.compressImage
import com.muhabbet.app.platform.rememberAudioPlayer
import com.muhabbet.app.platform.rememberAudioPermissionRequester
import com.muhabbet.app.platform.rememberAudioRecorder
import com.muhabbet.app.platform.rememberFilePickerLauncher
import com.muhabbet.app.platform.rememberImagePickerLauncher
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.LocationData
import com.muhabbet.shared.dto.PollData
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.shared.model.MessageStatus
import com.muhabbet.shared.model.PresenceStatus
import com.muhabbet.shared.protocol.AckStatus
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    conversationName: String,
    scrollToMessageId: String? = null,
    onBack: () -> Unit,
    onTitleClick: () -> Unit = {},
    onNavigateToConversation: ((conversationId: String, name: String) -> Unit)? = null,
    onMessageInfo: ((messageId: String) -> Unit)? = null,
    messageRepository: MessageRepository = koinInject(),
    mediaRepository: MediaRepository = koinInject(),
    groupRepository: GroupRepository = koinInject(),
    conversationRepository: ConversationRepository = koinInject(),
    wsClient: WsClient = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    // ── Core state ───────────────────────────
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var peerTyping by remember { mutableStateOf(false) }
    var peerOnline by remember { mutableStateOf(false) }
    var peerLastSeen by remember { mutableStateOf<Long?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }
    val snackbarHostState = remember { SnackbarHostState() }

    // Resolved strings for coroutine blocks
    val errorLoadMsg = stringResource(Res.string.error_load_messages)
    val errorSendMsg = stringResource(Res.string.error_send_failed)
    val typingText = stringResource(Res.string.chat_typing)
    val chatOnlineText = stringResource(Res.string.chat_online)
    val chatLastSeenText = stringResource(Res.string.chat_last_seen)
    val chatPhotoText = stringResource(Res.string.chat_photo)
    val chatVoiceText = stringResource(Res.string.chat_voice_message)
    val chatEditMode = stringResource(Res.string.chat_edit_mode)

    // Typing indicator
    var typingJob by remember { mutableStateOf<Job?>(null) }
    var isTypingSent by remember { mutableStateOf(false) }
    var typingDismissJob by remember { mutableStateOf<Job?>(null) }

    // Dialog state
    var fullImageUrl by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    var showDisappearDialog by remember { mutableStateOf(false) }
    var disappearAfterSeconds by remember { mutableStateOf<Int?>(null) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showGifPicker by remember { mutableStateOf(false) }

    // Message interaction
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var contextMenuMessageId by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var forwardMessage by remember { mutableStateOf<Message?>(null) }
    var forwardConversations by remember { mutableStateOf<List<ConversationResponse>>(emptyList()) }
    val starredIds = remember { mutableStateOf(setOf<String>()) }

    // Voice recording
    val audioRecorder = rememberAudioRecorder()
    val audioPlayer = rememberAudioPlayer()
    var isRecording by remember { mutableStateOf(false) }
    val requestAudioPermission = rememberAudioPermissionRequester { granted ->
        if (granted) { audioRecorder.startRecording(); isRecording = true }
    }

    // ── Media pickers ────────────────────────
    val filePickerLauncher = rememberFilePickerLauncher { picked ->
        if (picked == null) return@rememberFilePickerLauncher
        scope.launch {
            isUploading = true
            try {
                val upload = mediaRepository.uploadDocument(picked.bytes, picked.mimeType, picked.fileName)
                val msgId = generateMessageId(); val reqId = generateMessageId()
                messages = messages + Message(id = msgId, conversationId = conversationId, senderId = currentUserId,
                    contentType = ContentType.DOCUMENT, content = picked.fileName, mediaUrl = upload.url,
                    status = MessageStatus.SENDING, clientTimestamp = kotlinx.datetime.Clock.System.now())
                wsClient.send(WsMessage.SendMessage(requestId = reqId, messageId = msgId, conversationId = conversationId,
                    content = picked.fileName, contentType = ContentType.DOCUMENT, mediaUrl = upload.url))
            } catch (_: Exception) { snackbarHostState.showSnackbar(errorSendMsg) }
            isUploading = false
        }
    }

    val imagePickerLauncher = rememberImagePickerLauncher { picked ->
        if (picked == null) return@rememberImagePickerLauncher
        scope.launch {
            isUploading = true
            try {
                val upload = mediaRepository.uploadImage(compressImage(picked.bytes), "image/jpeg", picked.fileName)
                val msgId = generateMessageId(); val reqId = generateMessageId()
                messages = messages + Message(id = msgId, conversationId = conversationId, senderId = currentUserId,
                    contentType = ContentType.IMAGE, content = chatPhotoText, mediaUrl = upload.url,
                    thumbnailUrl = upload.thumbnailUrl, status = MessageStatus.SENDING, clientTimestamp = kotlinx.datetime.Clock.System.now())
                wsClient.send(WsMessage.SendMessage(requestId = reqId, messageId = msgId, conversationId = conversationId,
                    content = chatPhotoText, contentType = ContentType.IMAGE, mediaUrl = upload.url, thumbnailUrl = upload.thumbnailUrl))
            } catch (_: Exception) { snackbarHostState.showSnackbar(errorSendMsg) }
            isUploading = false
        }
    }

    // ── Data loading effects ─────────────────
    LaunchedEffect(conversationId) {
        try {
            disappearAfterSeconds = conversationRepository.getConversations().items.firstOrNull { it.id == conversationId }?.disappearAfterSeconds
        } catch (_: Exception) { }
    }

    LaunchedEffect(conversationId) {
        try {
            val result = messageRepository.getMessages(conversationId)
            messages = result.items.reversed(); nextCursor = result.nextCursor
        } catch (_: Exception) { snackbarHostState.showSnackbar(errorLoadMsg) }
        isLoading = false
        try {
            messages.lastOrNull { it.senderId != currentUserId }?.let {
                wsClient.send(WsMessage.AckMessage(messageId = it.id, conversationId = conversationId, status = MessageStatus.READ))
            }
        } catch (_: Exception) { }
    }

    // ── WebSocket listener ───────────────────
    LaunchedEffect(conversationId) {
        wsClient.incoming.collect { ws ->
            when (ws) {
                is WsMessage.NewMessage -> {
                    if (ws.conversationId == conversationId && messages.none { it.id == ws.messageId }) {
                        messages = messages + Message(id = ws.messageId, conversationId = ws.conversationId, senderId = ws.senderId,
                            contentType = ws.contentType, content = ws.content, replyToId = ws.replyToId, mediaUrl = ws.mediaUrl,
                            thumbnailUrl = ws.thumbnailUrl, serverTimestamp = kotlinx.datetime.Instant.fromEpochMilliseconds(ws.serverTimestamp),
                            clientTimestamp = kotlinx.datetime.Clock.System.now(), forwardedFrom = ws.forwardedFrom)
                        if (ws.senderId != currentUserId) {
                            try { wsClient.send(WsMessage.AckMessage(messageId = ws.messageId, conversationId = ws.conversationId, status = MessageStatus.READ)) } catch (_: Exception) { }
                        }
                    }
                }
                is WsMessage.ServerAck -> {
                    if (ws.status == AckStatus.OK) {
                        messages = messages.map { m -> if (m.id == ws.messageId) m.copy(status = MessageStatus.SENT, serverTimestamp = ws.serverTimestamp?.let { kotlinx.datetime.Instant.fromEpochMilliseconds(it) } ?: m.serverTimestamp) else m }
                    } else scope.launch { snackbarHostState.showSnackbar(errorSendMsg) }
                }
                is WsMessage.StatusUpdate -> if (ws.conversationId == conversationId) {
                    messages = if (ws.status == MessageStatus.READ) messages.map { m -> if (m.senderId == currentUserId && m.status in listOf(MessageStatus.SENT, MessageStatus.DELIVERED)) m.copy(status = MessageStatus.READ) else m }
                    else messages.map { m -> if (m.id == ws.messageId) m.copy(status = ws.status) else m }
                }
                is WsMessage.PresenceUpdate -> if (ws.userId != currentUserId) {
                    if (ws.conversationId == conversationId && ws.status == PresenceStatus.TYPING) {
                        peerTyping = true; typingDismissJob?.cancel(); typingDismissJob = scope.launch { delay(3000); peerTyping = false }
                    }
                    if (ws.conversationId == null) when (ws.status) {
                        PresenceStatus.ONLINE -> { peerOnline = true; peerLastSeen = null }
                        PresenceStatus.OFFLINE -> { peerOnline = false; peerLastSeen = ws.lastSeenAt }
                        PresenceStatus.TYPING -> {}
                    }
                }
                is WsMessage.MessageDeleted -> if (ws.conversationId == conversationId) messages = messages.map { m -> if (m.id == ws.messageId) m.copy(isDeleted = true, content = "") else m }
                is WsMessage.MessageEdited -> if (ws.conversationId == conversationId) messages = messages.map { m -> if (m.id == ws.messageId) m.copy(content = ws.newContent, editedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(ws.editedAt)) else m }
                is WsMessage.MessageReaction -> if (ws.conversationId == conversationId) {
                    messages = messages.map { m ->
                        if (m.id == ws.messageId) {
                            val newReactions = m.reactions.toMutableMap()
                            val newMyReactions = m.myReactions.toMutableSet()
                            if (ws.action == "add") {
                                newReactions[ws.emoji] = (newReactions[ws.emoji] ?: 0) + 1
                                if (ws.userId == currentUserId) newMyReactions.add(ws.emoji)
                            } else {
                                val c = (newReactions[ws.emoji] ?: 1) - 1
                                if (c <= 0) newReactions.remove(ws.emoji) else newReactions[ws.emoji] = c
                                if (ws.userId == currentUserId) newMyReactions.remove(ws.emoji)
                            }
                            m.copy(reactions = newReactions, myReactions = newMyReactions)
                        } else m
                    }
                }
                else -> {}
            }
        }
    }

    // Track whether this is the first load (instant scroll) vs subsequent updates (animate)
    var initialScrollDone by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        if (scrollToMessageId != null) {
            val index = messages.indexOfFirst { it.id == scrollToMessageId }
            if (index >= 0) { listState.animateScrollToItem(index); initialScrollDone = true; return@LaunchedEffect }
        }
        if (!initialScrollDone) {
            // First load: jump instantly to bottom (no visible scroll animation)
            listState.scrollToItem(messages.lastIndex)
            initialScrollDone = true
        } else {
            // New message arrived: animate to bottom
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { first ->
            if (first <= 1 && nextCursor != null && !isLoadingMore && !isLoading) {
                isLoadingMore = true
                try { val r = messageRepository.getMessages(conversationId, cursor = nextCursor); messages = r.items.reversed() + messages; nextCursor = r.nextCursor } catch (_: Exception) { }
                isLoadingMore = false
            }
        }
    }

    val subtitle = when {
        peerTyping -> typingText; peerOnline -> chatOnlineText
        peerLastSeen != null -> "$chatLastSeenText ${formatMessageTime(kotlinx.datetime.Instant.fromEpochMilliseconds(peerLastSeen!!))}"
        else -> null
    }

    // ── Dialogs ──────────────────────────────
    if (fullImageUrl != null) FullImageViewer(fullImageUrl!!) { fullImageUrl = null }
    if (forwardMessage != null) ForwardPickerDialog(forwardMessage!!, forwardConversations, conversationId, currentUserId, wsClient, scope, errorSendMsg, snackbarHostState, onDismiss = { forwardMessage = null }, onNavigateToConversation = onNavigateToConversation)
    if (showDeleteDialog && deleteTargetId != null) DeleteConfirmDialog(
        onConfirm = { val id = deleteTargetId!!; showDeleteDialog = false; deleteTargetId = null; scope.launch { try { groupRepository.deleteMessage(id); messages = messages.map { if (it.id == id) it.copy(isDeleted = true, content = "") else it } } catch (_: Exception) { snackbarHostState.showSnackbar(errorSendMsg) } } },
        onDismiss = { showDeleteDialog = false; deleteTargetId = null }
    )
    if (showDisappearDialog) DisappearTimerDialog(disappearAfterSeconds, onSelect = { s -> showDisappearDialog = false; disappearAfterSeconds = s; scope.launch { try { conversationRepository.setDisappearTimer(conversationId, s) } catch (_: Exception) { } } }, onDismiss = { showDisappearDialog = false })
    if (showLocationDialog) LocationShareDialog(onSend = { loc -> showLocationDialog = false; val json = kotlinx.serialization.json.Json.encodeToString(LocationData.serializer(), loc); val mid = generateMessageId(); val rid = generateMessageId(); messages = messages + Message(id = mid, conversationId = conversationId, senderId = currentUserId, contentType = ContentType.LOCATION, content = json, status = MessageStatus.SENDING, clientTimestamp = kotlinx.datetime.Clock.System.now()); scope.launch { try { wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = json, contentType = ContentType.LOCATION)) } catch (_: Exception) { messages = messages.filter { it.id != mid }; snackbarHostState.showSnackbar(errorSendMsg) } } }, onDismiss = { showLocationDialog = false })
    if (showPollDialog) PollCreateDialog(onSend = { poll -> showPollDialog = false; val json = kotlinx.serialization.json.Json.encodeToString(PollData.serializer(), poll); val mid = generateMessageId(); val rid = generateMessageId(); messages = messages + Message(id = mid, conversationId = conversationId, senderId = currentUserId, contentType = ContentType.POLL, content = json, status = MessageStatus.SENDING, clientTimestamp = kotlinx.datetime.Clock.System.now()); scope.launch { try { wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = json, contentType = ContentType.POLL)) } catch (_: Exception) { messages = messages.filter { it.id != mid }; snackbarHostState.showSnackbar(errorSendMsg) } } }, onDismiss = { showPollDialog = false })

    // ── Scaffold UI ──────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable { onTitleClick() }) {
                        Text(conversationName)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) } },
                actions = { IconButton(onClick = { showDisappearDialog = true }) { Icon(if (disappearAfterSeconds != null) Icons.Default.Timer else Icons.Default.TimerOff, contentDescription = null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, actionIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                var reactionTargetId by remember { mutableStateOf<String?>(null) }
                val showScrollToBottom = remember { derivedStateOf { val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; messages.isNotEmpty() && last < messages.lastIndex - 2 } }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (isLoadingMore) item(key = "loading_more") { Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } }
                        var lastDateStr = ""
                        messages.forEachIndexed { index, message ->
                            val dateStr = formatDateForSeparator(message.serverTimestamp ?: message.clientTimestamp)
                            if (dateStr != lastDateStr) { lastDateStr = dateStr; val d = dateStr; item(key = "date_$index") { DateSeparatorPill(d) } }
                            item(key = message.id) {
                                val isOwn = message.senderId == currentUserId
                                val repliedMessage = message.replyToId?.let { rid -> messages.firstOrNull { it.id == rid } }
                                val isStarred = message.id in starredIds.value
                                var swipeOffset by remember { mutableStateOf(0f) }

                                Box(modifier = Modifier.pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = { if (swipeOffset > 80f && !message.isDeleted) replyingTo = message; swipeOffset = 0f },
                                        onDragCancel = { swipeOffset = 0f },
                                        onHorizontalDrag = { _, d -> swipeOffset = (swipeOffset + d).coerceIn(0f, 120f) }
                                    )
                                }) {
                                    if (swipeOffset > 20f) Box(Modifier.align(Alignment.CenterStart).padding(start = 4.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.AutoMirrored.Filled.Reply, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = (swipeOffset / 80f).coerceIn(0f, 1f)))
                                    }
                                    Column(modifier = Modifier.padding(start = (swipeOffset / 3f).coerceAtMost(30f).dp)) {
                                        if (reactionTargetId == message.id) QuickReactionBar(visible = true, onReaction = { emoji -> reactionTargetId = null; scope.launch { try { messageRepository.addReaction(message.id, emoji) } catch (_: Exception) { } } })
                                        MessageBubble(message, isOwn, audioPlayer, repliedMessage, isStarred,
                                            showContextMenu = contextMenuMessageId == message.id,
                                            onLongPress = { if (!message.isDeleted) contextMenuMessageId = message.id },
                                            onDoubleTap = { if (!message.isDeleted) reactionTargetId = if (reactionTargetId == message.id) null else message.id },
                                            onDismissMenu = { contextMenuMessageId = null },
                                            onReply = { contextMenuMessageId = null; replyingTo = message },
                                            onForward = { contextMenuMessageId = null; forwardMessage = message; scope.launch { try { forwardConversations = conversationRepository.getConversations().items } catch (_: Exception) { } } },
                                            onStar = { contextMenuMessageId = null; scope.launch { try { if (isStarred) { messageRepository.unstarMessage(message.id); starredIds.value -= message.id } else { messageRepository.starMessage(message.id); starredIds.value += message.id } } catch (_: Exception) { } } },
                                            onEdit = { contextMenuMessageId = null; editingMessageId = message.id; messageText = message.content },
                                            onDelete = { contextMenuMessageId = null; deleteTargetId = message.id; showDeleteDialog = true },
                                            onImageClick = { fullImageUrl = it },
                                            onReactionToggle = { emoji ->
                                                scope.launch {
                                                    try {
                                                        if (emoji in message.myReactions) messageRepository.removeReaction(message.id, emoji)
                                                        else messageRepository.addReaction(message.id, emoji)
                                                    } catch (_: Exception) { }
                                                }
                                            },
                                            onInfo = { contextMenuMessageId = null; onMessageInfo?.invoke(message.id) }
                                        )
                                    }
                                }
                            }
                        }
                        if (peerTyping) item(key = "typing") { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) { TypingIndicatorBubble() } }
                    }
                    if (showScrollToBottom.value) {
                        Surface(onClick = { scope.launch { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) } }, shape = CircleShape, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.KeyboardArrowDown, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }

            // Reply / Edit bars
            replyingTo?.let { ReplyPreviewBar(it) { replyingTo = null } }
            if (editingMessageId != null) EditModeBar(chatEditMode) { editingMessageId = null; messageText = "" }

            // Voice recording or input bar
            if (isRecording) {
                Surface(tonalElevation = 2.dp) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        VoiceRecordButton(true, {}, onStopRecording = {
                            val audio = audioRecorder.stopRecording(); isRecording = false
                            if (audio != null) scope.launch {
                                isUploading = true
                                try {
                                    val upload = mediaRepository.uploadAudio(audio.bytes, audio.mimeType, "voice_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}.ogg", audio.durationSeconds)
                                    val mid = generateMessageId(); val rid = generateMessageId()
                                    messages = messages + Message(id = mid, conversationId = conversationId, senderId = currentUserId, contentType = ContentType.VOICE, content = chatVoiceText, mediaUrl = upload.url, status = MessageStatus.SENDING, clientTimestamp = kotlinx.datetime.Clock.System.now())
                                    wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = chatVoiceText, contentType = ContentType.VOICE, mediaUrl = upload.url))
                                } catch (_: Exception) { snackbarHostState.showSnackbar(errorSendMsg) }
                                isUploading = false
                            }
                        }, onCancelRecording = { audioRecorder.cancelRecording(); isRecording = false }, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                MessageInputBar(messageText,
                    onTextChange = { new ->
                        messageText = new
                        if (new.isNotEmpty() && editingMessageId == null) {
                            if (!isTypingSent) { scope.launch { try { wsClient.send(WsMessage.TypingIndicator(conversationId, true)) } catch (_: Exception) { } }; isTypingSent = true }
                            typingJob?.cancel(); typingJob = scope.launch { delay(3000); try { wsClient.send(WsMessage.TypingIndicator(conversationId, false)) } catch (_: Exception) { }; isTypingSent = false }
                        }
                    },
                    isEditing = editingMessageId != null, isUploading = isUploading,
                    onSend = {
                        if (messageText.isBlank()) return@MessageInputBar
                        if (editingMessageId != null) {
                            val id = editingMessageId!!; val content = messageText.trim(); editingMessageId = null; messageText = ""
                            scope.launch { try { groupRepository.editMessage(id, content); messages = messages.map { if (it.id == id) it.copy(content = content, editedAt = kotlinx.datetime.Clock.System.now()) else it } } catch (_: Exception) { snackbarHostState.showSnackbar(errorSendMsg) } }
                        } else {
                            val text = messageText; val replyId = replyingTo?.id; messageText = ""; replyingTo = null; typingJob?.cancel()
                            if (isTypingSent) { scope.launch { try { wsClient.send(WsMessage.TypingIndicator(conversationId, false)) } catch (_: Exception) { } }; isTypingSent = false }
                            val mid = generateMessageId(); val rid = generateMessageId()
                            messages = messages + Message(id = mid, conversationId = conversationId, senderId = currentUserId, contentType = ContentType.TEXT, content = text, replyToId = replyId, status = MessageStatus.SENDING, clientTimestamp = kotlinx.datetime.Clock.System.now())
                            scope.launch { try { wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = text, contentType = ContentType.TEXT, replyToId = replyId)) } catch (_: Exception) { messages = messages.filter { it.id != mid }; snackbarHostState.showSnackbar(errorSendMsg) } }
                        }
                    },
                    onMicClick = { if (audioRecorder.hasPermission()) { audioRecorder.startRecording(); isRecording = true } else requestAudioPermission() },
                    onImagePick = { imagePickerLauncher.launch() }, onFilePick = { filePickerLauncher.launch() },
                    onPollCreate = { showPollDialog = true }, onLocationShare = { showLocationDialog = true },
                    onGifPick = { showGifPicker = true }
                )
            }
        }
    }

    // GIF/Sticker picker
    if (showGifPicker) {
        GifStickerPicker(
            onDismiss = { showGifPicker = false },
            onGifSelected = { url, _ ->
                showGifPicker = false
                val mid = generateMessageId()
                val rid = generateMessageId()
                messages = messages + Message(id = mid, conversationId = conversationId, senderId = currentUserId, contentType = ContentType.GIF, content = "GIF", mediaUrl = url, status = MessageStatus.SENDING, clientTimestamp = kotlinx.datetime.Clock.System.now())
                scope.launch { try { wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = "GIF", contentType = ContentType.GIF, mediaUrl = url)) } catch (_: Exception) { messages = messages.filter { it.id != mid }; snackbarHostState.showSnackbar(errorSendMsg) } }
            },
            onStickerSelected = { url, _ ->
                showGifPicker = false
                val mid = generateMessageId()
                val rid = generateMessageId()
                messages = messages + Message(id = mid, conversationId = conversationId, senderId = currentUserId, contentType = ContentType.STICKER, content = "Sticker", mediaUrl = url, status = MessageStatus.SENDING, clientTimestamp = kotlinx.datetime.Clock.System.now())
                scope.launch { try { wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = "Sticker", contentType = ContentType.STICKER, mediaUrl = url)) } catch (_: Exception) { messages = messages.filter { it.id != mid }; snackbarHostState.showSnackbar(errorSendMsg) } }
            }
        )
    }
}
