package com.muhabbet.app.ui.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.platform.rememberAudioPlayer
import com.muhabbet.app.ui.chat.ForwardPickerDialog
import com.muhabbet.app.ui.chat.MediaViewer
import com.muhabbet.app.ui.chat.generateMessageId
import com.muhabbet.shared.model.ContentType
import com.muhabbet.shared.model.Message
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SharedMediaScreen(
    conversationId: String,
    onBack: () -> Unit,
    messageRepository: MessageRepository = koinInject(),
    groupRepository: GroupRepository = koinInject(),
    conversationRepository: ConversationRepository = koinInject(),
    wsClient: WsClient = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var mediaMessages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    val audioPlayer = rememberAudioPlayer()

    // Full-screen viewer state (images only)
    var viewerMessage by remember { mutableStateOf<Message?>(null) }

    // Context menu state
    var contextMenuMessage by remember { mutableStateOf<Message?>(null) }

    // Forward dialog state
    var forwardMessage by remember { mutableStateOf<Message?>(null) }
    var forwardConversations by remember { mutableStateOf<List<com.muhabbet.shared.dto.ConversationResponse>>(emptyList()) }

    // Currently playing voice
    var playingVoiceId by remember { mutableStateOf<String?>(null) }
    val isPlaying by audioPlayer.isPlaying.collectAsState()

    // Resolved strings for coroutine blocks
    val forwardText = stringResource(Res.string.media_viewer_forward)
    val deleteText = stringResource(Res.string.media_viewer_delete)
    val errorSendMsg = stringResource(Res.string.error_send_failed)

    // Cleanup audio on leave
    DisposableEffect(Unit) {
        onDispose { audioPlayer.stop(); audioPlayer.release() }
    }

    LaunchedEffect(conversationId) {
        try {
            val result = messageRepository.getMediaMessages(conversationId, limit = 100)
            mediaMessages = result.items
        } catch (_: Exception) { }
        isLoading = false
    }

    val imageVideos = mediaMessages.filter { it.contentType == ContentType.IMAGE || it.contentType == ContentType.VIDEO }
    val documents = mediaMessages.filter { it.contentType == ContentType.DOCUMENT || it.contentType == ContentType.VOICE }

    // Full-screen media viewer (images)
    viewerMessage?.let { msg ->
        MediaViewer(
            imageUrl = msg.mediaUrl ?: "",
            onDismiss = { viewerMessage = null },
            onForward = {
                val m = msg
                viewerMessage = null
                forwardMessage = m
                scope.launch {
                    try { forwardConversations = conversationRepository.getConversations().items }
                    catch (_: Exception) { }
                }
            },
            onDelete = if (msg.senderId == currentUserId) {
                {
                    val id = msg.id
                    viewerMessage = null
                    scope.launch {
                        try {
                            groupRepository.deleteMessage(id)
                            mediaMessages = mediaMessages.filter { it.id != id }
                        } catch (_: Exception) { }
                    }
                }
            } else null
        )
    }

    // Forward picker dialog
    if (forwardMessage != null) {
        ForwardPickerDialog(
            forwardMessage = forwardMessage!!,
            forwardConversations = forwardConversations,
            conversationId = conversationId,
            currentUserId = currentUserId,
            wsClient = wsClient,
            scope = scope,
            errorSendMsg = errorSendMsg,
            snackbarHostState = snackbarHostState,
            onDismiss = { forwardMessage = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.shared_media_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(Res.string.shared_media_images)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(Res.string.shared_media_documents)) }
                )
            }

            AnimatedVisibility(
                visible = !isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Crossfade(targetState = selectedTab) { tab ->
                    when {
                        tab == 0 && imageVideos.isEmpty() || tab == 1 && documents.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(Res.string.shared_media_empty),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        tab == 0 -> {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(imageVideos, key = { it.id }) { message ->
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(4.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    if (message.contentType == ContentType.VIDEO) {
                                                        // Open video in external player
                                                        message.mediaUrl?.let { url ->
                                                            try { uriHandler.openUri(url) } catch (_: Exception) { }
                                                        }
                                                    } else {
                                                        viewerMessage = message
                                                    }
                                                },
                                                onLongClick = { contextMenuMessage = message }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = message.thumbnailUrl ?: message.mediaUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (message.contentType == ContentType.VIDEO) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        // Long-press context menu
                                        DropdownMenu(
                                            expanded = contextMenuMessage?.id == message.id,
                                            onDismissRequest = { contextMenuMessage = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(forwardText) },
                                                onClick = {
                                                    val m = message
                                                    contextMenuMessage = null
                                                    forwardMessage = m
                                                    scope.launch {
                                                        try { forwardConversations = conversationRepository.getConversations().items }
                                                        catch (_: Exception) { }
                                                    }
                                                }
                                            )
                                            if (message.senderId == currentUserId) {
                                                DropdownMenuItem(
                                                    text = { Text(deleteText, color = MaterialTheme.colorScheme.error) },
                                                    onClick = {
                                                        val id = message.id
                                                        contextMenuMessage = null
                                                        scope.launch {
                                                            try {
                                                                groupRepository.deleteMessage(id)
                                                                mediaMessages = mediaMessages.filter { it.id != id }
                                                            } catch (_: Exception) { }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        tab == 1 -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(documents, key = { it.id }) { message ->
                                    val isVoice = message.contentType == ContentType.VOICE
                                    val isThisPlaying = playingVoiceId == message.id && isPlaying

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    if (isVoice) {
                                                        // Toggle voice playback
                                                        if (isThisPlaying) {
                                                            audioPlayer.pause()
                                                        } else {
                                                            message.mediaUrl?.let { url ->
                                                                audioPlayer.stop()
                                                                playingVoiceId = message.id
                                                                audioPlayer.play(url)
                                                            }
                                                        }
                                                    } else {
                                                        // Open document in external viewer
                                                        message.mediaUrl?.let { url ->
                                                            try { uriHandler.openUri(url) } catch (_: Exception) { }
                                                        }
                                                    }
                                                },
                                                onLongClick = { contextMenuMessage = message }
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isVoice) {
                                            Icon(
                                                if (isThisPlaying) Icons.Default.Pause else Icons.Default.Mic,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = if (isThisPlaying) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Description,
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = message.content.ifBlank { if (isVoice) "Voice" else "Document" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Long-press context menu for documents
                                        DropdownMenu(
                                            expanded = contextMenuMessage?.id == message.id,
                                            onDismissRequest = { contextMenuMessage = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(forwardText) },
                                                onClick = {
                                                    val m = message
                                                    contextMenuMessage = null
                                                    forwardMessage = m
                                                    scope.launch {
                                                        try { forwardConversations = conversationRepository.getConversations().items }
                                                        catch (_: Exception) { }
                                                    }
                                                }
                                            )
                                            if (message.senderId == currentUserId) {
                                                DropdownMenuItem(
                                                    text = { Text(deleteText, color = MaterialTheme.colorScheme.error) },
                                                    onClick = {
                                                        val id = message.id
                                                        contextMenuMessage = null
                                                        scope.launch {
                                                            try {
                                                                groupRepository.deleteMessage(id)
                                                                mediaMessages = mediaMessages.filter { it.id != id }
                                                            } catch (_: Exception) { }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
