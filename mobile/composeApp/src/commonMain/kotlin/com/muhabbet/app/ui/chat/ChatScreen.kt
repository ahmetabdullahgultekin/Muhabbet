package com.muhabbet.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.MessageRepository
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
    onBack: () -> Unit,
    messageRepository: MessageRepository = koinInject(),
    wsClient: WsClient = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var peerTyping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val currentUserId = tokenStorage.getUserId()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorLoadMsg = stringResource(Res.string.error_load_messages)
    val errorSendMsg = stringResource(Res.string.error_send_failed)
    val typingText = stringResource(Res.string.chat_typing)

    // Typing indicator state
    var typingJob by remember { mutableStateOf<Job?>(null) }
    var isTypingSent by remember { mutableStateOf(false) }
    var typingDismissJob by remember { mutableStateOf<Job?>(null) }

    // Load initial messages
    LaunchedEffect(conversationId) {
        try {
            val result = messageRepository.getMessages(conversationId)
            messages = result.items.reversed() // oldest first
            nextCursor = result.nextCursor
        } catch (_: Exception) {
            snackbarHostState.showSnackbar(errorLoadMsg)
        }
        isLoading = false
    }

    // Listen for real-time WS messages
    LaunchedEffect(conversationId) {
        wsClient.incoming.collect { wsMessage ->
            when (wsMessage) {
                is WsMessage.NewMessage -> {
                    if (wsMessage.conversationId == conversationId) {
                        val now = kotlinx.datetime.Clock.System.now()
                        val serverTs = kotlinx.datetime.Instant.fromEpochMilliseconds(wsMessage.serverTimestamp)
                        val newMsg = Message(
                            id = wsMessage.messageId,
                            conversationId = wsMessage.conversationId,
                            senderId = wsMessage.senderId,
                            contentType = wsMessage.contentType,
                            content = wsMessage.content,
                            serverTimestamp = serverTs,
                            clientTimestamp = now
                        )
                        messages = messages + newMsg
                        // Send DELIVERED ack
                        if (wsMessage.senderId != currentUserId) {
                            wsClient.send(
                                WsMessage.AckMessage(
                                    messageId = wsMessage.messageId,
                                    conversationId = wsMessage.conversationId,
                                    status = MessageStatus.DELIVERED
                                )
                            )
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
                    if (wsMessage.conversationId == conversationId &&
                        wsMessage.status == PresenceStatus.TYPING &&
                        wsMessage.userId != currentUserId
                    ) {
                        peerTyping = true
                        typingDismissJob?.cancel()
                        typingDismissJob = scope.launch {
                            delay(3000)
                            peerTyping = false
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

    // Pagination — load older messages when scrolled to top
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (listState.firstVisibleItemIndex <= 1 && nextCursor != null && !isLoadingMore && !isLoading) {
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(conversationName)
                        if (peerTyping) {
                            Text(
                                text = typingText,
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
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                    state = listState,
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
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            isOwn = message.senderId == currentUserId
                        )
                    }
                }
            }

            // Message input
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { newText ->
                        messageText = newText
                        // Send typing indicator
                        if (newText.isNotEmpty()) {
                            if (!isTypingSent) {
                                scope.launch {
                                    wsClient.send(WsMessage.TypingIndicator(conversationId, true))
                                }
                                isTypingSent = true
                            }
                            typingJob?.cancel()
                            typingJob = scope.launch {
                                delay(3000)
                                wsClient.send(WsMessage.TypingIndicator(conversationId, false))
                                isTypingSent = false
                            }
                        }
                    },
                    placeholder = { Text(stringResource(Res.string.chat_message_placeholder)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )

                Spacer(Modifier.width(8.dp))

                FilledIconButton(
                    onClick = {
                        if (messageText.isBlank()) return@FilledIconButton
                        val text = messageText
                        messageText = ""
                        // Cancel typing indicator
                        typingJob?.cancel()
                        if (isTypingSent) {
                            scope.launch {
                                wsClient.send(WsMessage.TypingIndicator(conversationId, false))
                            }
                            isTypingSent = false
                        }
                        scope.launch {
                            val messageId = generateMessageId()
                            val requestId = generateMessageId()
                            wsClient.send(
                                WsMessage.SendMessage(
                                    requestId = requestId,
                                    messageId = messageId,
                                    conversationId = conversationId,
                                    content = text,
                                    contentType = ContentType.TEXT
                                )
                            )
                            // Optimistic: add to local list
                            val now = kotlinx.datetime.Clock.System.now()
                            val optimistic = Message(
                                id = messageId,
                                conversationId = conversationId,
                                senderId = currentUserId ?: "",
                                contentType = ContentType.TEXT,
                                content = text,
                                status = MessageStatus.SENDING,
                                clientTimestamp = now
                            )
                            messages = messages + optimistic
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

@Composable
private fun MessageBubble(message: Message, isOwn: Boolean) {
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
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.content,
                    color = if (isOwn) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Timestamp + delivery status row
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val timestamp = message.serverTimestamp ?: message.clientTimestamp
                    Text(
                        text = formatMessageTime(timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
    // Format as HH:mm using epoch math (KMP-compatible)
    val epochSeconds = instant.epochSeconds
    val totalMinutes = epochSeconds / 60
    val hours = ((totalMinutes / 60) % 24).toInt()
    val minutes = (totalMinutes % 60).toInt()
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
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
