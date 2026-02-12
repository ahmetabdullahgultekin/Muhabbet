package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.CircularProgressIndicator
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
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.platform.ImagePickerLauncher
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.app.platform.compressImage
import com.muhabbet.app.platform.rememberImagePickerLauncher
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    conversationName: String,
    onBack: () -> Unit,
    messageRepository: MessageRepository = koinInject(),
    mediaRepository: MediaRepository = koinInject(),
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

    // Typing indicator state
    var typingJob by remember { mutableStateOf<Job?>(null) }
    var isTypingSent by remember { mutableStateOf(false) }
    var typingDismissJob by remember { mutableStateOf<Job?>(null) }

    // Full image viewer state
    var fullImageUrl by remember { mutableStateOf<String?>(null) }

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
                    content = "Foto\u011Fra\u0066",
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
                        content = "Foto\u011Fra\u0066",
                        contentType = ContentType.IMAGE,
                        mediaUrl = uploadResponse.url
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
                        messages = messages.map { msg ->
                            if (msg.id == wsMessage.messageId) {
                                msg.copy(status = wsMessage.status)
                            } else msg
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
        peerOnline -> "\u00E7evrimi\u00E7i"
        peerLastSeen != null -> {
            val instant = kotlinx.datetime.Instant.fromEpochMilliseconds(peerLastSeen!!)
            "son g\u00F6r\u00FClme ${formatMessageTime(instant)}"
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
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
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isOwn = message.senderId == currentUserId,
                            onImageClick = { url -> fullImageUrl = url }
                        )
                    }
                }
            }

            // Message input
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attach image button
                    IconButton(
                        onClick = { imagePickerLauncher.launch() },
                        enabled = !isUploading
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

                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { newText ->
                            messageText = newText
                            if (newText.isNotEmpty()) {
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

                    FilledIconButton(
                        onClick = {
                            if (messageText.isBlank()) return@FilledIconButton
                            val text = messageText
                            messageText = ""
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
                                            contentType = ContentType.TEXT
                                        )
                                    )
                                } catch (e: Exception) {
                                    messages = messages.filter { it.id != messageId }
                                    snackbarHostState.showSnackbar(errorSendMsg)
                                }
                            }
                        },
                        enabled = messageText.isNotBlank(),
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isOwn: Boolean,
    onImageClick: (String) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwn) 16.dp else 4.dp,
                bottomEnd = if (isOwn) 4.dp else 16.dp
            ),
            color = if (isOwn) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = if (isOwn) 0.dp else 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(min = 80.dp, max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
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

                // Text content (skip for images with default placeholder text)
                if (message.contentType == ContentType.TEXT ||
                    (message.contentType == ContentType.IMAGE && message.content != "Foto\u011Fra\u0066" && message.content.isNotBlank())
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                }

                // Timestamp + delivery status row
                Row(
                    modifier = Modifier.align(Alignment.End).padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val timestamp = message.serverTimestamp ?: message.clientTimestamp
                    Text(
                        text = formatMessageTime(timestamp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    if (isOwn) {
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
