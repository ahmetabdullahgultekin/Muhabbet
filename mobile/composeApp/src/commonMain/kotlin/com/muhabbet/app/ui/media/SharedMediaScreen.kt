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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.muhabbet.designsystem.components.MuhabbetMenu
import com.muhabbet.designsystem.components.MuhabbetMenuItem
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
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
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetEmptyState

private const val TAG = "SharedMediaScreen"

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
    val errorLoadMsg = stringResource(Res.string.error_load_failed)
    val errorLoadConversationsMsg = stringResource(Res.string.error_load_conversations)
    val errorDeleteMsg = stringResource(Res.string.error_delete_failed)
    val errorOpenMsg = stringResource(Res.string.error_open_external)
    val photoLabel = stringResource(Res.string.chat_photo)
    val videoLabel = stringResource(Res.string.chat_video)
    val playLabel = stringResource(Res.string.voice_play)
    val pauseLabel = stringResource(Res.string.voice_pause)
    val voiceMessageLabel = stringResource(Res.string.chat_voice_message)
    val documentLabel = stringResource(Res.string.attach_document)

    // Cleanup audio on leave
    DisposableEffect(Unit) {
        onDispose { audioPlayer.stop(); audioPlayer.release() }
    }

    // The forward / delete / open-externally actions appear three times over (viewer, image grid,
    // document list); the failure handling lives here once so every entry point reports the same way.
    fun loadForwardTargets() {
        scope.launch {
            runCatchingCancellable {
                forwardConversations = conversationRepository.getConversations().items
            }.onFailure { e ->
                // Otherwise the picker opens empty and reads as "no chats to forward to".
                Log.e(TAG, "Failed to load forward targets", e)
                snackbarHostState.showSnackbar(errorLoadConversationsMsg)
            }
        }
    }

    fun deleteMediaMessage(id: String) {
        scope.launch {
            runCatchingCancellable {
                groupRepository.deleteMessage(id)
                mediaMessages = mediaMessages.filter { it.id != id }
            }.onFailure { e ->
                // The tile stays on screen on failure — say why instead of looking like a no-op.
                Log.e(TAG, "Failed to delete message $id", e)
                snackbarHostState.showSnackbar(errorDeleteMsg)
            }
        }
    }

    fun openExternally(url: String) {
        runCatchingCancellable { uriHandler.openUri(url) }
            .onFailure { e ->
                // No handler app installed, or a malformed URL — the tap looks dead otherwise.
                Log.e(TAG, "Failed to open media externally", e)
                scope.launch { snackbarHostState.showSnackbar(errorOpenMsg) }
            }
    }

    LaunchedEffect(conversationId) {
        val failure = runCatchingCancellable {
            val result = messageRepository.getMediaMessages(conversationId, limit = 100)
            mediaMessages = result.items
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        if (failure != null) {
            // Without this the screen shows the "no shared media" empty state, which is a lie.
            Log.e(TAG, "Failed to load shared media", failure)
            snackbarHostState.showSnackbar(errorLoadMsg)
        }
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
                loadForwardTargets()
            },
            onDelete = if (msg.senderId == currentUserId) {
                {
                    val id = msg.id
                    viewerMessage = null
                    deleteMediaMessage(id)
                }
            } else null
        )
    }

    // Forward picker dialog
    forwardMessage?.let { msgToForward ->
        ForwardPickerDialog(
            forwardMessage = msgToForward,
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

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.shared_media_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        snackbarHostState = snackbarHostState
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
                            MuhabbetEmptyState(
                modifier = Modifier.fillMaxSize(),
                icon = Muhabbet.icons.Image,
                title = stringResource(Res.string.shared_media_empty)
            )
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
                                            .clip(MaterialTheme.shapes.extraSmall)
                                            .combinedClickable(
                                                onClick = {
                                                    if (message.contentType == ContentType.VIDEO) {
                                                        // Open video in external player
                                                        message.mediaUrl?.let { url -> openExternally(url) }
                                                    } else {
                                                        viewerMessage = message
                                                    }
                                                },
                                                onLongClick = { contextMenuMessage = message }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // The whole cell is the control and carries no text, so the
                                        // thumbnail is what a screen reader has to announce.
                                        AsyncImage(
                                            model = message.thumbnailUrl ?: message.mediaUrl,
                                            contentDescription = if (message.contentType == ContentType.VIDEO) videoLabel else photoLabel,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (message.contentType == ContentType.VIDEO) {
                                            Icon(
                                                Muhabbet.icons.Play,
                                                // Decorative: the thumbnail above already says "Video".
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }

                                        // Long-press context menu
                                        MuhabbetMenu(
                                            expanded = contextMenuMessage?.id == message.id,
                                            onDismissRequest = { contextMenuMessage = null }
                                        ) {
                                            MuhabbetMenuItem(
                                                text = forwardText,
                                                onClick = {
                                                    val m = message
                                                    contextMenuMessage = null
                                                    forwardMessage = m
                                                    loadForwardTargets()
                                                }
                                            )
                                            if (message.senderId == currentUserId) {
                                                MuhabbetMenuItem(
                                                    text = deleteText,
                                                    destructive = true,
                                                    onClick = {
                                                        val id = message.id
                                                        contextMenuMessage = null
                                                        deleteMediaMessage(id)
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
                                                        message.mediaUrl?.let { url -> openExternally(url) }
                                                    }
                                                },
                                                onLongClick = { contextMenuMessage = message }
                                            )
                                            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isVoice) {
                                            // Carries state — whether this clip is playing — that no
                                            // adjacent text repeats, so it must be described.
                                            Icon(
                                                if (isThisPlaying) Muhabbet.icons.Pause else Muhabbet.icons.Mic,
                                                contentDescription = if (isThisPlaying) pauseLabel else playLabel,
                                                modifier = Modifier.size(MuhabbetSizes.IconLarge),
                                                tint = if (isThisPlaying) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            Icon(
                                                Muhabbet.icons.Document,
                                                // Decorative: the document name sits right beside it.
                                                contentDescription = null,
                                                modifier = Modifier.size(MuhabbetSizes.IconLarge),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(Modifier.width(MuhabbetSpacing.Medium))
                                        Text(
                                            text = message.content.ifBlank { if (isVoice) voiceMessageLabel else documentLabel },
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        // Long-press context menu for documents
                                        MuhabbetMenu(
                                            expanded = contextMenuMessage?.id == message.id,
                                            onDismissRequest = { contextMenuMessage = null }
                                        ) {
                                            MuhabbetMenuItem(
                                                text = forwardText,
                                                onClick = {
                                                    val m = message
                                                    contextMenuMessage = null
                                                    forwardMessage = m
                                                    loadForwardTargets()
                                                }
                                            )
                                            if (message.senderId == currentUserId) {
                                                MuhabbetMenuItem(
                                                    text = deleteText,
                                                    destructive = true,
                                                    onClick = {
                                                        val id = message.id
                                                        contextMenuMessage = null
                                                        deleteMediaMessage(id)
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
                MuhabbetLoadingState(Modifier.fillMaxSize())
            }
        }
    }
}
