package com.muhabbet.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.snapshotFlow
import coil3.compose.AsyncImage
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.app.platform.AudioPlayer
import com.muhabbet.app.platform.FilePickerLauncher
import com.muhabbet.app.platform.ImagePickerLauncher
import com.muhabbet.app.platform.PickedFile
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.app.platform.compressImage
import com.muhabbet.app.platform.rememberAudioPlayer
import com.muhabbet.app.platform.rememberAudioPermissionRequester
import com.muhabbet.app.platform.rememberAudioRecorder
import com.muhabbet.app.platform.rememberFilePickerLauncher
import com.muhabbet.app.platform.rememberImagePickerLauncher
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    conversationName: String,
    onBack: () -> Unit,
    onTitleClick: () -> Unit = {},
    messageRepository: MessageRepository = koinInject(),
    mediaRepository: MediaRepository = koinInject(),
    groupRepository: GroupRepository = koinInject(),
    conversationRepository: ConversationRepository = koinInject(),
    wsClient: WsClient = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
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

    val errorLoadMsg = stringResource(Res.string.error_load_messages)
    val errorSendMsg = stringResource(Res.string.error_send_failed)
    val typingText = stringResource(Res.string.chat_typing)
    val chatOnlineText = stringResource(Res.string.chat_online)
    val chatLastSeenText = stringResource(Res.string.chat_last_seen)
    val chatPhotoText = stringResource(Res.string.chat_photo)
    val chatVoiceText = stringResource(Res.string.chat_voice_message)
    val chatDeleteTitle = stringResource(Res.string.chat_delete_title)
    val chatDeleteConfirm = stringResource(Res.string.chat_delete_confirm)
    val chatEditMode = stringResource(Res.string.chat_edit_mode)
    val chatDeletedText = stringResource(Res.string.chat_message_deleted)
    val chatEditedText = stringResource(Res.string.chat_edited)
    val chatContextEdit = stringResource(Res.string.chat_context_edit)
    val chatContextDelete = stringResource(Res.string.chat_context_delete)
    val cancelText = stringResource(Res.string.cancel)
    val deleteText = stringResource(Res.string.delete)

    // Typing indicator state
    var typingJob by remember { mutableStateOf<Job?>(null) }
    var isTypingSent by remember { mutableStateOf(false) }
    var typingDismissJob by remember { mutableStateOf<Job?>(null) }

    // Full image viewer state
    var fullImageUrl by remember { mutableStateOf<String?>(null) }

    // Voice recording state
    val audioRecorder = rememberAudioRecorder()
    val audioPlayer = rememberAudioPlayer()
    var isRecording by remember { mutableStateOf(false) }
    val requestAudioPermission = rememberAudioPermissionRequester { granted ->
        if (granted) {
            audioRecorder.startRecording()
            isRecording = true
        }
    }

    // Message edit/delete/reply state
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var contextMenuMessageId by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var forwardMessage by remember { mutableStateOf<Message?>(null) }
    var forwardConversations by remember { mutableStateOf<List<ConversationResponse>>(emptyList()) }
    val starredIds = remember { mutableStateOf(setOf<String>()) }

    // Attach menu state
    var showAttachMenu by remember { mutableStateOf(false) }

    // Disappearing messages state
    var showDisappearDialog by remember { mutableStateOf(false) }
    var disappearAfterSeconds by remember { mutableStateOf<Int?>(null) }

    // Load conversation disappear setting
    LaunchedEffect(conversationId) {
        try {
            val convs = conversationRepository.getConversations()
            val conv = convs.items.firstOrNull { it.id == conversationId }
            disappearAfterSeconds = conv?.disappearAfterSeconds
        } catch (_: Exception) { }
    }

    // Poll creation state
    var showPollDialog by remember { mutableStateOf(false) }
    var pollQuestion by remember { mutableStateOf("") }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }

    // Location sharing state
    var showLocationDialog by remember { mutableStateOf(false) }
    var locationLabel by remember { mutableStateOf("") }
    var locationLat by remember { mutableStateOf("") }
    var locationLng by remember { mutableStateOf("") }

    // File picker
    val filePickerLauncher: FilePickerLauncher = rememberFilePickerLauncher { picked: PickedFile? ->
        if (picked == null) return@rememberFilePickerLauncher
        scope.launch {
            isUploading = true
            try {
                val uploadResponse = mediaRepository.uploadDocument(
                    bytes = picked.bytes,
                    mimeType = picked.mimeType,
                    fileName = picked.fileName
                )
                val messageId = generateMessageId()
                val requestId = generateMessageId()
                val now = kotlinx.datetime.Clock.System.now()
                val optimistic = Message(
                    id = messageId,
                    conversationId = conversationId,
                    senderId = currentUserId,
                    contentType = ContentType.DOCUMENT,
                    content = picked.fileName,
                    mediaUrl = uploadResponse.url,
                    status = MessageStatus.SENDING,
                    clientTimestamp = now
                )
                messages = messages + optimistic
                wsClient.send(
                    WsMessage.SendMessage(
                        requestId = requestId,
                        messageId = messageId,
                        conversationId = conversationId,
                        content = picked.fileName,
                        contentType = ContentType.DOCUMENT,
                        mediaUrl = uploadResponse.url
                    )
                )
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(errorSendMsg)
            }
            isUploading = false
        }
    }

    // Image picker
    val imagePickerLauncher: ImagePickerLauncher = rememberImagePickerLauncher { picked: PickedImage? ->
        if (picked == null) return@rememberImagePickerLauncher
        scope.launch {
            isUploading = true
            try {
                val compressed = compressImage(picked.bytes)
                val uploadResponse = mediaRepository.uploadImage(
                    bytes = compressed,
                    mimeType = "image/jpeg",
                    fileName = picked.fileName
                )
                // Send image message via WS
                val messageId = generateMessageId()
                val requestId = generateMessageId()
                val now = kotlinx.datetime.Clock.System.now()
                val optimistic = Message(
                    id = messageId,
                    conversationId = conversationId,
                    senderId = currentUserId,
                    contentType = ContentType.IMAGE,
                    content = chatPhotoText,
                    mediaUrl = uploadResponse.url,
                    thumbnailUrl = uploadResponse.thumbnailUrl,
                    status = MessageStatus.SENDING,
                    clientTimestamp = now
                )
                messages = messages + optimistic
                wsClient.send(
                    WsMessage.SendMessage(
                        requestId = requestId,
                        messageId = messageId,
                        conversationId = conversationId,
                        content = chatPhotoText,
                        contentType = ContentType.IMAGE,
                        mediaUrl = uploadResponse.url,
                        thumbnailUrl = uploadResponse.thumbnailUrl
                    )
                )
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(errorSendMsg)
            }
            isUploading = false
        }
    }

    // Load initial messages
    LaunchedEffect(conversationId) {
        try {
            val result = messageRepository.getMessages(conversationId)
            messages = result.items.reversed()
            nextCursor = result.nextCursor
        } catch (_: Exception) {
            snackbarHostState.showSnackbar(errorLoadMsg)
        }
        isLoading = false
        try {
            messages.lastOrNull { it.senderId != currentUserId }?.let { lastMsg ->
                wsClient.send(
                    WsMessage.AckMessage(
                        messageId = lastMsg.id,
                        conversationId = conversationId,
                        status = MessageStatus.READ
                    )
                )
            }
        } catch (_: Exception) { }
    }

    // Listen for real-time WS messages
    LaunchedEffect(conversationId) {
        wsClient.incoming.collect { wsMessage ->
            when (wsMessage) {
                is WsMessage.NewMessage -> {
                    if (wsMessage.conversationId == conversationId) {
                        if (messages.any { it.id == wsMessage.messageId }) return@collect
                        val now = kotlinx.datetime.Clock.System.now()
                        val serverTs = kotlinx.datetime.Instant.fromEpochMilliseconds(wsMessage.serverTimestamp)
                        val newMsg = Message(
                            id = wsMessage.messageId,
                            conversationId = wsMessage.conversationId,
                            senderId = wsMessage.senderId,
                            contentType = wsMessage.contentType,
                            content = wsMessage.content,
                            replyToId = wsMessage.replyToId,
                            mediaUrl = wsMessage.mediaUrl,
                            thumbnailUrl = wsMessage.thumbnailUrl,
                            serverTimestamp = serverTs,
                            clientTimestamp = now
                        )
                        messages = messages + newMsg
                        if (wsMessage.senderId != currentUserId) {
                            try {
                                wsClient.send(
                                    WsMessage.AckMessage(
                                        messageId = wsMessage.messageId,
                                        conversationId = wsMessage.conversationId,
                                        status = MessageStatus.READ
                                    )
                                )
                            } catch (_: Exception) { }
                        }
                    }
                }
                is WsMessage.ServerAck -> {
                    if (wsMessage.status == AckStatus.OK) {
                        messages = messages.map { msg ->
                            if (msg.id == wsMessage.messageId) {
                                val ts = wsMessage.serverTimestamp?.let {
                                    kotlinx.datetime.Instant.fromEpochMilliseconds(it)
                                }
                                msg.copy(
                                    status = MessageStatus.SENT,
                                    serverTimestamp = ts ?: msg.serverTimestamp
                                )
                            } else msg
                        }
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(errorSendMsg) }
                    }
                }
                is WsMessage.StatusUpdate -> {
                    if (wsMessage.conversationId == conversationId) {
                        if (wsMessage.status == MessageStatus.READ) {
                            // Bulk-update: mark all own SENT/DELIVERED messages as READ
                            messages = messages.map { msg ->
                                if (msg.senderId == currentUserId &&
                                    (msg.status == MessageStatus.SENT || msg.status == MessageStatus.DELIVERED)
                                ) {
                                    msg.copy(status = MessageStatus.READ)
                                } else msg
                            }
                        } else {
                            messages = messages.map { msg ->
                                if (msg.id == wsMessage.messageId) {
                                    msg.copy(status = wsMessage.status)
                                } else msg
                            }
                        }
                    }
                }
                is WsMessage.PresenceUpdate -> {
                    if (wsMessage.userId != currentUserId) {
                        if (wsMessage.conversationId == conversationId &&
                            wsMessage.status == PresenceStatus.TYPING
                        ) {
                            peerTyping = true
                            typingDismissJob?.cancel()
                            typingDismissJob = scope.launch {
                                delay(3000)
                                peerTyping = false
                            }
                        }
                        // Global presence update (conversationId == null)
                        if (wsMessage.conversationId == null) {
                            when (wsMessage.status) {
                                PresenceStatus.ONLINE -> {
                                    peerOnline = true
                                    peerLastSeen = null
                                }
                                PresenceStatus.OFFLINE -> {
                                    peerOnline = false
                                    peerLastSeen = wsMessage.lastSeenAt
                                }
                                PresenceStatus.TYPING -> {}
                            }
                        }
                    }
                }
                is WsMessage.MessageDeleted -> {
                    if (wsMessage.conversationId == conversationId) {
                        messages = messages.map { msg ->
                            if (msg.id == wsMessage.messageId) msg.copy(isDeleted = true, content = "")
                            else msg
                        }
                    }
                }
                is WsMessage.MessageEdited -> {
                    if (wsMessage.conversationId == conversationId) {
                        messages = messages.map { msg ->
                            if (msg.id == wsMessage.messageId) {
                                val editedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(wsMessage.editedAt)
                                msg.copy(content = wsMessage.newContent, editedAt = editedAt)
                            } else msg
                        }
                    }
                }
                is WsMessage.GroupMemberAdded,
                is WsMessage.GroupMemberRemoved,
                is WsMessage.GroupInfoUpdated,
                is WsMessage.GroupRoleUpdated,
                is WsMessage.GroupMemberLeft -> { /* group events — handled by ConversationList refresh */ }
                else -> {}
            }
        }
    }

    // Auto-scroll on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // Pagination
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisible ->
                if (firstVisible <= 1 && nextCursor != null && !isLoadingMore && !isLoading) {
                    isLoadingMore = true
                    try {
                        val result = messageRepository.getMessages(conversationId, cursor = nextCursor)
                        val olderMessages = result.items.reversed()
                        messages = olderMessages + messages
                        nextCursor = result.nextCursor
                    } catch (_: Exception) { }
                    isLoadingMore = false
                }
            }
    }

    // Subtitle: typing > online > last seen
    val subtitle = when {
        peerTyping -> typingText
        peerOnline -> chatOnlineText
        peerLastSeen != null -> {
            val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(peerLastSeen!!)
            "$chatLastSeenText ${formatMessageTime(instant)}"
        }
        else -> null
    }

    // Full image viewer dialog
    if (fullImageUrl != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { fullImageUrl = null }) {
            Box(
                modifier = Modifier.fillMaxSize().clickable { fullImageUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    // Forward picker dialog
    if (forwardMessage != null) {
        AlertDialog(
            onDismissRequest = { forwardMessage = null },
            title = { Text(stringResource(Res.string.chat_forward_title)) },
            text = {
                if (forwardConversations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(forwardConversations.filter { it.id != conversationId }, key = { it.id }) { conv ->
                            val convName = conv.name
                                ?: conv.participants.firstOrNull { it.userId != currentUserId }?.displayName
                                ?: conv.participants.firstOrNull { it.userId != currentUserId }?.phoneNumber
                                ?: ""
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val msg = forwardMessage!!
                                        forwardMessage = null
                                        scope.launch {
                                            try {
                                                val messageId = generateMessageId()
                                                val requestId = generateMessageId()
                                                wsClient.send(
                                                    WsMessage.SendMessage(
                                                        requestId = requestId,
                                                        messageId = messageId,
                                                        conversationId = conv.id,
                                                        content = msg.content,
                                                        contentType = msg.contentType,
                                                        mediaUrl = msg.mediaUrl,
                                                        thumbnailUrl = msg.thumbnailUrl
                                                    )
                                                )
                                            } catch (_: Exception) {
                                                snackbarHostState.showSnackbar(errorSendMsg)
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                com.muhabbet.app.ui.components.UserAvatar(
                                    avatarUrl = conv.avatarUrl,
                                    displayName = convName,
                                    size = 36.dp,
                                    isGroup = conv.type == com.muhabbet.shared.model.ConversationType.GROUP
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(convName, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { forwardMessage = null }) {
                    Text(cancelText)
                }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteDialog && deleteTargetId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; deleteTargetId = null },
            title = { Text(chatDeleteTitle) },
            text = { Text(chatDeleteConfirm) },
            confirmButton = {
                TextButton(onClick = {
                    val msgId = deleteTargetId!!
                    showDeleteDialog = false
                    deleteTargetId = null
                    scope.launch {
                        try {
                            groupRepository.deleteMessage(msgId)
                            messages = messages.map { msg ->
                                if (msg.id == msgId) msg.copy(isDeleted = true, content = "")
                                else msg
                            }
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(errorSendMsg)
                        }
                    }
                }) { Text(deleteText, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false; deleteTargetId = null }) {
                    Text(cancelText)
                }
            }
        )
    }

    // Disappearing messages timer dialog
    if (showDisappearDialog) {
        val disappearOffText = stringResource(Res.string.disappear_off)
        val disappear30sText = stringResource(Res.string.disappear_30s)
        val disappear5mText = stringResource(Res.string.disappear_5m)
        val disappear1hText = stringResource(Res.string.disappear_1h)
        val disappear1dText = stringResource(Res.string.disappear_1d)
        val disappear1wText = stringResource(Res.string.disappear_1w)
        val timerOptions = listOf(
            null to disappearOffText,
            30 to disappear30sText,
            300 to disappear5mText,
            3600 to disappear1hText,
            86400 to disappear1dText,
            604800 to disappear1wText
        )
        AlertDialog(
            onDismissRequest = { showDisappearDialog = false },
            title = { Text(stringResource(Res.string.disappear_title)) },
            text = {
                Column {
                    timerOptions.forEach { (seconds, label) ->
                        val isSelected = disappearAfterSeconds == seconds
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showDisappearDialog = false
                                    disappearAfterSeconds = seconds
                                    scope.launch {
                                        try {
                                            conversationRepository.setDisappearTimer(conversationId, seconds)
                                        } catch (_: Exception) { }
                                    }
                                },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDisappearDialog = false }) {
                    Text(cancelText)
                }
            }
        )
    }

    // Location sharing dialog
    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = {
                showLocationDialog = false
                locationLabel = ""; locationLat = ""; locationLng = ""
            },
            title = { Text(stringResource(Res.string.location_share_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = locationLabel,
                        onValueChange = { locationLabel = it },
                        placeholder = { Text(stringResource(Res.string.location_label_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = locationLat,
                        onValueChange = { locationLat = it },
                        placeholder = { Text(stringResource(Res.string.location_lat_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = locationLng,
                        onValueChange = { locationLng = it },
                        placeholder = { Text(stringResource(Res.string.location_lng_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val lat = locationLat.toDoubleOrNull()
                        val lng = locationLng.toDoubleOrNull()
                        if (lat != null && lng != null) {
                            showLocationDialog = false
                            val locData = LocationData(
                                latitude = lat,
                                longitude = lng,
                                label = locationLabel.takeIf { it.isNotBlank() }
                            )
                            val locJson = kotlinx.serialization.json.Json.encodeToString(LocationData.serializer(), locData)
                            val messageId = generateMessageId()
                            val requestId = generateMessageId()
                            val now = kotlinx.datetime.Clock.System.now()
                            val optimistic = Message(
                                id = messageId,
                                conversationId = conversationId,
                                senderId = currentUserId,
                                contentType = ContentType.LOCATION,
                                content = locJson,
                                status = MessageStatus.SENDING,
                                clientTimestamp = now
                            )
                            messages = messages + optimistic
                            scope.launch {
                                try {
                                    wsClient.send(
                                        WsMessage.SendMessage(
                                            requestId = requestId,
                                            messageId = messageId,
                                            conversationId = conversationId,
                                            content = locJson,
                                            contentType = ContentType.LOCATION
                                        )
                                    )
                                } catch (e: Exception) {
                                    messages = messages.filter { it.id != messageId }
                                    snackbarHostState.showSnackbar(errorSendMsg)
                                }
                            }
                            locationLabel = ""; locationLat = ""; locationLng = ""
                        }
                    },
                    enabled = locationLat.toDoubleOrNull() != null && locationLng.toDoubleOrNull() != null
                ) { Text(stringResource(Res.string.poll_send)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLocationDialog = false
                    locationLabel = ""; locationLat = ""; locationLng = ""
                }) { Text(cancelText) }
            }
        )
    }

    // Poll creation dialog
    if (showPollDialog) {
        AlertDialog(
            onDismissRequest = {
                showPollDialog = false
                pollQuestion = ""
                pollOptions = listOf("", "")
            },
            title = { Text(stringResource(Res.string.poll_create_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pollQuestion,
                        onValueChange = { pollQuestion = it },
                        placeholder = { Text(stringResource(Res.string.poll_question_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    pollOptions.forEachIndexed { index, option ->
                        OutlinedTextField(
                            value = option,
                            onValueChange = { newVal ->
                                pollOptions = pollOptions.toMutableList().also { it[index] = newVal }
                            },
                            placeholder = { Text(stringResource(Res.string.poll_option_placeholder, index + 1)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                    if (pollOptions.size < 6) {
                        TextButton(onClick = { pollOptions = pollOptions + "" }) {
                            Text(stringResource(Res.string.poll_add_option))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val q = pollQuestion.trim()
                        val opts = pollOptions.map { it.trim() }.filter { it.isNotEmpty() }
                        if (q.isNotEmpty() && opts.size >= 2) {
                            showPollDialog = false
                            val pollData = PollData(question = q, options = opts)
                            val pollJson = kotlinx.serialization.json.Json.encodeToString(PollData.serializer(), pollData)
                            val messageId = generateMessageId()
                            val requestId = generateMessageId()
                            val now = kotlinx.datetime.Clock.System.now()
                            val optimistic = Message(
                                id = messageId,
                                conversationId = conversationId,
                                senderId = currentUserId,
                                contentType = ContentType.POLL,
                                content = pollJson,
                                status = MessageStatus.SENDING,
                                clientTimestamp = now
                            )
                            messages = messages + optimistic
                            scope.launch {
                                try {
                                    wsClient.send(
                                        WsMessage.SendMessage(
                                            requestId = requestId,
                                            messageId = messageId,
                                            conversationId = conversationId,
                                            content = pollJson,
                                            contentType = ContentType.POLL
                                        )
                                    )
                                } catch (e: Exception) {
                                    messages = messages.filter { it.id != messageId }
                                    snackbarHostState.showSnackbar(errorSendMsg)
                                }
                            }
                            pollQuestion = ""
                            pollOptions = listOf("", "")
                        }
                    },
                    enabled = pollQuestion.isNotBlank() && pollOptions.count { it.isNotBlank() } >= 2
                ) { Text(stringResource(Res.string.poll_send)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPollDialog = false
                    pollQuestion = ""
                    pollOptions = listOf("", "")
                }) { Text(cancelText) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable { onTitleClick() }) {
                        Text(conversationName)
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDisappearDialog = true }) {
                        Icon(
                            imageVector = if (disappearAfterSeconds != null) Icons.Default.Timer else Icons.Default.TimerOff,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
        ) {
            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Reaction state
                var reactionTargetId by remember { mutableStateOf<String?>(null) }

                // Scroll-to-bottom state
                val showScrollToBottom = remember {
                    androidx.compose.runtime.derivedStateOf {
                        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        messages.isNotEmpty() && lastVisible < messages.lastIndex - 2
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isLoadingMore) {
                            item(key = "loading_more") {
                                Box(
                                    Modifier.fillMaxWidth().padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                        // Messages with date separators
                        var lastDateStr = ""
                        messages.forEachIndexed { index, message ->
                            val msgTimestamp = message.serverTimestamp ?: message.clientTimestamp
                            val dateStr = formatDateForSeparator(msgTimestamp)
                            if (dateStr != lastDateStr) {
                                lastDateStr = dateStr
                                val capturedDate = dateStr
                                item(key = "date_$index") {
                                    DateSeparatorPill(date = capturedDate)
                                }
                            }
                            item(key = message.id) {
                                val isOwn = message.senderId == currentUserId
                                val repliedMessage = message.replyToId?.let { rid -> messages.firstOrNull { it.id == rid } }
                                val isStarred = message.id in starredIds.value

                                // Message with swipe-to-reply
                                var swipeOffset by remember { mutableStateOf(0f) }
                                Box(
                                    modifier = Modifier
                                        .pointerInput(Unit) {
                                            detectHorizontalDragGestures(
                                                onDragEnd = {
                                                    if (swipeOffset > 80f && !message.isDeleted) {
                                                        replyingTo = message
                                                    }
                                                    swipeOffset = 0f
                                                },
                                                onDragCancel = { swipeOffset = 0f },
                                                onHorizontalDrag = { _, dragAmount ->
                                                    swipeOffset = (swipeOffset + dragAmount).coerceIn(0f, 120f)
                                                }
                                            )
                                        }
                                ) {
                                    // Reply indicator
                                    if (swipeOffset > 20f) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .padding(start = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Reply,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary.copy(
                                                    alpha = (swipeOffset / 80f).coerceIn(0f, 1f)
                                                )
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.padding(
                                            start = (swipeOffset / 3f).coerceAtMost(30f).dp
                                        )
                                    ) {
                                        // Reaction bar (shown on double-tap)
                                        if (reactionTargetId == message.id) {
                                            QuickReactionBar(
                                                visible = true,
                                                onReaction = { emoji ->
                                                    reactionTargetId = null
                                                    scope.launch {
                                                        try {
                                                            messageRepository.addReaction(message.id, emoji)
                                                        } catch (_: Exception) { }
                                                    }
                                                }
                                            )
                                        }

                                        MessageBubble(
                                            message = message,
                                            isOwn = isOwn,
                                            audioPlayer = audioPlayer,
                                            repliedMessage = repliedMessage,
                                            isStarred = isStarred,
                                            showContextMenu = contextMenuMessageId == message.id,
                                            onLongPress = { if (!message.isDeleted) contextMenuMessageId = message.id },
                                            onDoubleTap = {
                                                if (!message.isDeleted) {
                                                    reactionTargetId = if (reactionTargetId == message.id) null else message.id
                                                }
                                            },
                                            onDismissMenu = { contextMenuMessageId = null },
                                            onReply = {
                                                contextMenuMessageId = null
                                                replyingTo = message
                                            },
                                            onForward = {
                                                contextMenuMessageId = null
                                                forwardMessage = message
                                                scope.launch {
                                                    try {
                                                        forwardConversations = conversationRepository.getConversations().items
                                                    } catch (_: Exception) { }
                                                }
                                            },
                                            onStar = {
                                                contextMenuMessageId = null
                                                scope.launch {
                                                    try {
                                                        if (isStarred) {
                                                            messageRepository.unstarMessage(message.id)
                                                            starredIds.value = starredIds.value - message.id
                                                        } else {
                                                            messageRepository.starMessage(message.id)
                                                            starredIds.value = starredIds.value + message.id
                                                        }
                                                    } catch (_: Exception) { }
                                                }
                                            },
                                            onEdit = {
                                                contextMenuMessageId = null
                                                editingMessageId = message.id
                                                messageText = message.content
                                            },
                                            onDelete = {
                                                contextMenuMessageId = null
                                                deleteTargetId = message.id
                                                showDeleteDialog = true
                                            },
                                            onImageClick = { url -> fullImageUrl = url }
                                        )
                                    }
                                }
                            }
                        }

                        // Animated typing indicator
                        if (peerTyping) {
                            item(key = "typing_indicator") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    TypingIndicatorBubble()
                                }
                            }
                        }
                    }

                    // Scroll-to-bottom FAB
                    AnimatedVisibility(
                        visible = showScrollToBottom.value,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                    ) {
                        Surface(
                            onClick = {
                                scope.launch {
                                    if (messages.isNotEmpty()) {
                                        listState.animateScrollToItem(messages.lastIndex)
                                    }
                                }
                            },
                            shape = CircleShape,
                            shadowElevation = 6.dp,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Reply preview bar
            if (replyingTo != null) {
                Surface(tonalElevation = 4.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.chat_replying_to),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = replyingTo!!.content.take(50),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(
                            onClick = { replyingTo = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Edit mode indicator
            if (editingMessageId != null) {
                Surface(tonalElevation = 4.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = chatEditMode,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                editingMessageId = null
                                messageText = ""
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Voice recording bar
            if (isRecording) {
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VoiceRecordButton(
                            isRecording = true,
                            onStartRecording = {},
                            onStopRecording = {
                                val audio = audioRecorder.stopRecording()
                                isRecording = false
                                if (audio != null) {
                                    scope.launch {
                                        isUploading = true
                                        try {
                                            val uploadResponse = mediaRepository.uploadAudio(
                                                bytes = audio.bytes,
                                                mimeType = audio.mimeType,
                                                fileName = "voice_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}.ogg",
                                                durationSeconds = audio.durationSeconds
                                            )
                                            val messageId = generateMessageId()
                                            val requestId = generateMessageId()
                                            val now = kotlinx.datetime.Clock.System.now()
                                            val optimistic = Message(
                                                id = messageId,
                                                conversationId = conversationId,
                                                senderId = currentUserId,
                                                contentType = ContentType.VOICE,
                                                content = chatVoiceText,
                                                mediaUrl = uploadResponse.url,
                                                status = MessageStatus.SENDING,
                                                clientTimestamp = now
                                            )
                                            messages = messages + optimistic
                                            wsClient.send(
                                                WsMessage.SendMessage(
                                                    requestId = requestId,
                                                    messageId = messageId,
                                                    conversationId = conversationId,
                                                    content = chatVoiceText,
                                                    contentType = ContentType.VOICE,
                                                    mediaUrl = uploadResponse.url
                                                )
                                            )
                                        } catch (_: Exception) {
                                            snackbarHostState.showSnackbar(errorSendMsg)
                                        }
                                        isUploading = false
                                    }
                                }
                            },
                            onCancelRecording = {
                                audioRecorder.cancelRecording()
                                isRecording = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Message input
                Surface(tonalElevation = 2.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attach button with menu
                        Box {
                            IconButton(
                                onClick = { showAttachMenu = true },
                                enabled = !isUploading && editingMessageId == null
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AttachFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.attach_image)) },
                                    onClick = { showAttachMenu = false; imagePickerLauncher.launch() },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.attach_document)) },
                                    onClick = { showAttachMenu = false; filePickerLauncher.launch() },
                                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.attach_poll)) },
                                    onClick = { showAttachMenu = false; showPollDialog = true },
                                    leadingIcon = { Icon(Icons.Default.Poll, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.attach_location)) },
                                    onClick = { showAttachMenu = false; showLocationDialog = true },
                                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { newText ->
                                messageText = newText
                                if (newText.isNotEmpty() && editingMessageId == null) {
                                    if (!isTypingSent) {
                                        scope.launch {
                                            try { wsClient.send(WsMessage.TypingIndicator(conversationId, true)) } catch (_: Exception) { }
                                        }
                                        isTypingSent = true
                                    }
                                    typingJob?.cancel()
                                    typingJob = scope.launch {
                                        delay(3000)
                                        try { wsClient.send(WsMessage.TypingIndicator(conversationId, false)) } catch (_: Exception) { }
                                        isTypingSent = false
                                    }
                                }
                            },
                            placeholder = { Text(stringResource(Res.string.chat_message_placeholder)) },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp)
                        )

                        Spacer(Modifier.width(4.dp))

                        if (messageText.isBlank() && editingMessageId == null) {
                            // Mic button when text is empty
                            FilledIconButton(
                                onClick = {
                                    if (audioRecorder.hasPermission()) {
                                        audioRecorder.startRecording()
                                        isRecording = true
                                    } else {
                                        requestAudioPermission()
                                    }
                                },
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            // Send / Edit confirm button
                            FilledIconButton(
                                onClick = {
                                    if (messageText.isBlank()) return@FilledIconButton

                                    if (editingMessageId != null) {
                                        // Edit mode — send edit request
                                        val msgId = editingMessageId!!
                                        val newContent = messageText.trim()
                                        editingMessageId = null
                                        messageText = ""
                                        scope.launch {
                                            try {
                                                groupRepository.editMessage(msgId, newContent)
                                                val editedAt = kotlinx.datetime.Clock.System.now()
                                                messages = messages.map { msg ->
                                                    if (msg.id == msgId) msg.copy(content = newContent, editedAt = editedAt)
                                                    else msg
                                                }
                                            } catch (_: Exception) {
                                                snackbarHostState.showSnackbar(errorSendMsg)
                                            }
                                        }
                                    } else {
                                        // Normal send
                                        val text = messageText
                                        val replyId = replyingTo?.id
                                        messageText = ""
                                        replyingTo = null
                                        typingJob?.cancel()
                                        if (isTypingSent) {
                                            scope.launch {
                                                try { wsClient.send(WsMessage.TypingIndicator(conversationId, false)) } catch (_: Exception) { }
                                            }
                                            isTypingSent = false
                                        }
                                        val messageId = generateMessageId()
                                        val requestId = generateMessageId()
                                        val now = kotlinx.datetime.Clock.System.now()
                                        val optimistic = Message(
                                            id = messageId,
                                            conversationId = conversationId,
                                            senderId = currentUserId,
                                            contentType = ContentType.TEXT,
                                            content = text,
                                            replyToId = replyId,
                                            status = MessageStatus.SENDING,
                                            clientTimestamp = now
                                        )
                                        messages = messages + optimistic
                                        scope.launch {
                                            try {
                                                wsClient.send(
                                                    WsMessage.SendMessage(
                                                        requestId = requestId,
                                                        messageId = messageId,
                                                        conversationId = conversationId,
                                                        content = text,
                                                        contentType = ContentType.TEXT,
                                                        replyToId = replyId
                                                    )
                                                )
                                            } catch (e: Exception) {
                                                messages = messages.filter { it.id != messageId }
                                                snackbarHostState.showSnackbar(errorSendMsg)
                                            }
                                        }
                                    }
                                },
                                enabled = messageText.isNotBlank(),
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = if (editingMessageId != null) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary,
                                    contentColor = if (editingMessageId != null) MaterialTheme.colorScheme.onTertiary
                                    else MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (editingMessageId != null) Icons.Default.Check
                                    else Icons.AutoMirrored.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    audioPlayer: AudioPlayer,
    repliedMessage: Message? = null,
    isStarred: Boolean = false,
    showContextMenu: Boolean = false,
    onLongPress: () -> Unit = {},
    onDoubleTap: () -> Unit = {},
    onDismissMenu: () -> Unit = {},
    onReply: () -> Unit = {},
    onForward: () -> Unit = {},
    onStar: () -> Unit = {},
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onImageClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isOwn) 16.dp else 4.dp,
                    bottomEnd = if (isOwn) 4.dp else 16.dp
                ),
                color = if (message.isDeleted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else if (isOwn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isOwn) 0.dp else 1.dp,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .widthIn(min = 80.dp, max = 300.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = onLongPress,
                        onDoubleClick = onDoubleTap
                    )
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    // Quoted reply
                    if (repliedMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isOwn) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Row(modifier = Modifier.padding(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .then(
                                            Modifier.padding(0.dp)
                                        )
                                )
                                Column(modifier = Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = repliedMessage.content.take(60),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }

                    if (message.isDeleted) {
                        // Deleted message placeholder
                        Text(
                            text = stringResource(Res.string.chat_message_deleted),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else {
                        // Voice content
                        if (message.contentType == ContentType.VOICE && message.mediaUrl != null) {
                            VoiceBubble(
                                mediaUrl = message.mediaUrl!!,
                                durationSeconds = null,
                                isOwn = isOwn,
                                audioPlayer = audioPlayer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }

                        // Document content
                        if (message.contentType == ContentType.DOCUMENT && message.mediaUrl != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isOwn) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .clickable { /* open URL */ }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = if (isOwn) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = message.content,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }

                        // Location content
                        if (message.contentType == ContentType.LOCATION) {
                            LocationBubble(
                                content = message.content,
                                isOwn = isOwn,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Poll content
                        if (message.contentType == ContentType.POLL) {
                            PollBubble(
                                messageId = message.id,
                                pollContent = message.content,
                                isOwn = isOwn,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Image content
                        if (message.contentType == ContentType.IMAGE && (message.mediaUrl != null || message.thumbnailUrl != null)) {
                            val imageUrl = message.thumbnailUrl ?: message.mediaUrl
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { message.mediaUrl?.let { onImageClick(it) } },
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.height(4.dp))
                        }

                        // Text content (skip for images/voice with default placeholder text)
                        if (message.contentType == ContentType.TEXT ||
                            (message.contentType == ContentType.IMAGE && message.content != stringResource(Res.string.chat_photo) && message.content.isNotBlank())
                        ) {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            // Link preview for text messages containing URLs
                            if (message.contentType == ContentType.TEXT) {
                                val firstUrl = extractFirstUrl(message.content)
                                if (firstUrl != null) {
                                    LinkPreviewCard(url = firstUrl, isOwn = isOwn)
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                        }
                    }

                    // Timestamp + edited + delivery status row
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (message.editedAt != null && !message.isDeleted) {
                            Text(
                                text = stringResource(Res.string.chat_edited),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }

                        val timestamp = message.serverTimestamp ?: message.clientTimestamp
                        Text(
                            text = formatMessageTime(timestamp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        if (isOwn && !message.isDeleted) {
                            val (icon, tint) = when (message.status) {
                                MessageStatus.SENDING -> Icons.Default.AccessTime to
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                MessageStatus.SENT -> Icons.Default.Check to
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll to
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                MessageStatus.READ -> Icons.Default.DoneAll to
                                        MaterialTheme.colorScheme.tertiary
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = tint
                            )
                        }
                    }
                }
            }

            // Context menu
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = onDismissMenu
            ) {
                // Reply — available for all messages
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.chat_context_reply)) },
                    onClick = onReply,
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )
                // Forward — available for all messages
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.chat_context_forward)) },
                    onClick = onForward,
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                )
                // Star/Unstar — available for all messages
                DropdownMenuItem(
                    text = { Text(if (isStarred) stringResource(Res.string.chat_context_unstar) else stringResource(Res.string.chat_context_star)) },
                    onClick = onStar,
                    leadingIcon = {
                        Icon(
                            if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isStarred) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                )
                // Edit/Delete — only for own messages
                if (isOwn) {
                    if (message.contentType == ContentType.TEXT) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.chat_context_edit)) },
                            onClick = onEdit,
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.chat_context_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = onDelete,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun formatMessageTime(instant: kotlinx.datetime.Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

private fun generateMessageId(): String {
    val chars = "0123456789abcdef"
    return buildString {
        repeat(8) { append(chars.random()) }
        append('-')
        repeat(4) { append(chars.random()) }
        append('-')
        append('4')
        repeat(3) { append(chars.random()) }
        append('-')
        append(listOf('8', '9', 'a', 'b').random())
        repeat(3) { append(chars.random()) }
        append('-')
        repeat(12) { append(chars.random()) }
    }
}
