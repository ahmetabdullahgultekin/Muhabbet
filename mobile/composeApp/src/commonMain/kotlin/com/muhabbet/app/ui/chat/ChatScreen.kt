package com.muhabbet.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.remote.MessageQueuedException
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.ui.connection.ConnectionStrip
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.data.repository.MediaUploadHelper
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.platform.AppVisibility
import com.muhabbet.app.platform.rememberAudioPlayer
import com.muhabbet.app.platform.rememberAudioPermissionRequester
import com.muhabbet.app.platform.rememberAudioRecorder
import com.muhabbet.app.platform.rememberCameraPickerLauncher
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.Instant
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.generateMessageId
import com.muhabbet.app.util.runCatchingCancellable
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.app.ui.transition.handoffAvatar
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTopBarDefaults
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.MuhabbetLoadingState

private const val TAG = "ChatScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    conversationName: String,
    conversationAvatarUrl: String? = null,
    isGroup: Boolean = false,
    scrollToMessageId: String? = null,
    onBack: () -> Unit,
    onTitleClick: () -> Unit = {},
    onNavigateToConversation: ((conversationId: String, name: String) -> Unit)? = null,
    onMessageInfo: ((messageId: String) -> Unit)? = null,
    messageRepository: MessageRepository = koinInject(),
    mediaUploadHelper: MediaUploadHelper = koinInject(),
    groupRepository: GroupRepository = koinInject(),
    conversationRepository: ConversationRepository = koinInject(),
    wsClient: WsClient = koinInject(),
    tokenStorage: TokenStorage = koinInject(),
    appVisibility: AppVisibility = koinInject()
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
    // Which messages this screen has already told the sender about. Keyed on the conversation, so
    // walking into a different chat starts clean. See AckedMessageIds for why the receipt cannot be
    // gated on the message list any more (#478).
    val ackedMessageIds = remember(conversationId) { AckedMessageIds() }
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    // Rendered by the ConnectionStrip below. Nothing had ever read this flow before #511.
    val connectionState by wsClient.connectionState.collectAsState()

    // Resolved strings for coroutine blocks
    val errorLoadMsg = stringResource(Res.string.error_load_messages)
    val errorSendMsg = stringResource(Res.string.error_send_failed)
    val typingText = stringResource(Res.string.chat_typing)
    val chatOnlineText = stringResource(Res.string.chat_online)
    val chatLastSeenText = stringResource(Res.string.chat_last_seen)
    val chatPhotoText = stringResource(Res.string.chat_photo)
    val chatVoiceText = stringResource(Res.string.chat_voice_message)
    val chatEditMode = stringResource(Res.string.chat_edit_mode)
    val gifContentLabel = stringResource(Res.string.attach_gif)
    val stickerContentLabel = stringResource(Res.string.attach_sticker)
    val scheduleQueuedMsg = stringResource(Res.string.schedule_queued)
    val scheduleCancelledMsg = stringResource(Res.string.schedule_cancelled)
    val errorLoadConversationsMsg = stringResource(Res.string.error_load_conversations)
    val errorActionMsg = stringResource(Res.string.error_action_failed)
    val errorDisappearingMsg = stringResource(Res.string.error_disappearing_timer_failed)
    val errorOpenMsg = stringResource(Res.string.error_open_failed)
    val errorVideoUnavailableMsg = stringResource(Res.string.error_video_unavailable)
    val groupAvatarLabel = stringResource(Res.string.cd_group_avatar)

    // One place decides what a send that did not reach the wire looks like.
    //
    // A message the socket *queued* is not a failure: it is sitting in the offline queue and will
    // go out on the next connect. Deleting its bubble and reporting "could not send" — which is
    // what every one of these call sites used to do — tells the user something untrue and invites
    // them to type it again, so it arrives twice. A queued message therefore stays exactly where it
    // is, still MessageStatus.SENDING, which already renders as a pending clock, and the
    // ConnectionStrip above explains why it is still waiting. Only a genuine failure removes the
    // bubble and says so. (#511)
    suspend fun reportSendOutcome(messageId: String, error: Throwable) {
        if (error is MessageQueuedException) return
        messages = messages.filter { it.id != messageId }
        snackbarHostState.showSnackbar(errorSendMsg)
    }

    // Documents, link previews and shared locations all end here. Opening can genuinely fail —
    // nothing installed that handles the URL, or the platform refusing it — and a tap that opens
    // nothing is indistinguishable from the dead bubbles this replaces, so it has to say so.
    fun openExternally(url: String) {
        runCatchingCancellable { uriHandler.openUri(url) }
            .onFailure { e ->
                Log.e(TAG, "Failed to open $url externally", e)
                scope.launch { snackbarHostState.showSnackbar(errorOpenMsg) }
            }
    }

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
    // Non-null while the shared GIF/sticker sheet is open; the value is the tab it opened on.
    var gifPickerTab by remember { mutableStateOf<GifStickerTab?>(null) }

    // Scheduled send — session-local pending list + dialog visibility
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showScheduledListDialog by remember { mutableStateOf(false) }
    var pendingScheduled by remember { mutableStateOf<List<PendingScheduledMessage>>(emptyList()) }

    // Message interaction
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var contextMenuMessageId by remember { mutableStateOf<String?>(null) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var forwardMessage by remember { mutableStateOf<Message?>(null) }
    var forwardConversations by remember { mutableStateOf<List<ConversationResponse>>(emptyList()) }
    val starredIds = remember { mutableStateOf(setOf<String>()) }

    // View-once and announcement mode.
    //
    // The toggle lives in the attachment sheet since #479, but the state stays here: it has to
    // outlive the sheet, which dismisses before the picker even opens. It applies to the next photo
    // and then clears itself, which is why both image paths below reset it after sending.
    //
    // Until #479 it was also never put on the wire — `WsMessage.SendMessage.viewOnce` defaulted to
    // false and neither picker passed it, so the sender saw a sealed local bubble, the server stored
    // an ordinary message, and the recipient (and the sender after a reload) got the photo in full.
    var viewOnceEnabled by remember { mutableStateOf(false) }
    var isAnnouncementOnly by remember { mutableStateOf(false) }
    var isAdminOrOwner by remember { mutableStateOf(false) }

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
            var sendFailed = false
            try {
                val upload = mediaUploadHelper.uploadDocument(picked.bytes, picked.fileName, picked.mimeType)
                val msgId = generateMessageId(); val reqId = generateMessageId()
                messages = messages + Message(id = msgId, conversationId = conversationId, senderId = currentUserId,
                    contentType = ContentType.DOCUMENT, content = picked.fileName, mediaUrl = upload.url,
                    status = MessageStatus.SENDING, clientTimestamp = Clock.System.now())
                wsClient.send(WsMessage.SendMessage(requestId = reqId, messageId = msgId, conversationId = conversationId,
                    content = picked.fileName, contentType = ContentType.DOCUMENT, mediaUrl = upload.url))
            } catch (_: Exception) { sendFailed = true }
            // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
            isUploading = false
            if (sendFailed) snackbarHostState.showSnackbar(errorSendMsg)
        }
    }

    val imagePickerLauncher = rememberImagePickerLauncher { picked ->
        if (picked == null) return@rememberImagePickerLauncher
        scope.launch {
            isUploading = true
            var sendFailed = false
            try {
                val upload = mediaUploadHelper.uploadImage(picked.bytes, picked.fileName)
                val msgId = generateMessageId(); val reqId = generateMessageId()
                messages = messages + Message(id = msgId, conversationId = conversationId, senderId = currentUserId,
                    contentType = ContentType.IMAGE, content = chatPhotoText, mediaUrl = upload.url,
                    thumbnailUrl = upload.thumbnailUrl, status = MessageStatus.SENDING, clientTimestamp = Clock.System.now(),
                    viewOnce = viewOnceEnabled)
                wsClient.send(WsMessage.SendMessage(requestId = reqId, messageId = msgId, conversationId = conversationId,
                    content = chatPhotoText, contentType = ContentType.IMAGE, mediaUrl = upload.url,
                    thumbnailUrl = upload.thumbnailUrl, viewOnce = viewOnceEnabled))
            } catch (_: Exception) { sendFailed = true }
            // Disarmed on the way out of the attempt, not inside the `try`: `wsClient.send` throws
            // when the socket is down, so clearing it there left the flag set after a failure with
            // the sheet closed — armed, invisible, and applied to whatever photo came next.
            viewOnceEnabled = false
            // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
            isUploading = false
            if (sendFailed) snackbarHostState.showSnackbar(errorSendMsg)
        }
    }

    // Camera picker
    val cameraPickerLauncher = rememberCameraPickerLauncher { picked ->
        if (picked == null) return@rememberCameraPickerLauncher
        scope.launch {
            isUploading = true
            var sendFailed = false
            try {
                val upload = mediaUploadHelper.uploadImage(picked.bytes, picked.fileName)
                val msgId = generateMessageId(); val reqId = generateMessageId()
                messages = messages + Message(id = msgId, conversationId = conversationId, senderId = currentUserId,
                    contentType = ContentType.IMAGE, content = chatPhotoText, mediaUrl = upload.url,
                    thumbnailUrl = upload.thumbnailUrl, status = MessageStatus.SENDING, clientTimestamp = Clock.System.now(),
                    viewOnce = viewOnceEnabled)
                wsClient.send(WsMessage.SendMessage(requestId = reqId, messageId = msgId, conversationId = conversationId,
                    content = chatPhotoText, contentType = ContentType.IMAGE, mediaUrl = upload.url,
                    thumbnailUrl = upload.thumbnailUrl, viewOnce = viewOnceEnabled))
            } catch (_: Exception) { sendFailed = true }
            // Disarmed on the way out of the attempt, not inside the `try`: `wsClient.send` throws
            // when the socket is down, so clearing it there left the flag set after a failure with
            // the sheet closed — armed, invisible, and applied to whatever photo came next.
            viewOnceEnabled = false
            // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
            isUploading = false
            if (sendFailed) snackbarHostState.showSnackbar(errorSendMsg)
        }
    }

    // ── Data loading effects ─────────────────
    LaunchedEffect(conversationId) {
        runCatchingCancellable {
            val conv = conversationRepository.getConversations().items.firstOrNull { it.id == conversationId }
            disappearAfterSeconds = conv?.disappearAfterSeconds
            isAnnouncementOnly = conv?.announcementOnly ?: false
            val myParticipant = conv?.participants?.firstOrNull { it.userId == currentUserId }
            isAdminOrOwner = myParticipant?.role == com.muhabbet.shared.model.MemberRole.OWNER ||
                myParticipant?.role == com.muhabbet.shared.model.MemberRole.ADMIN
        }.onFailure { e ->
            // Best-effort enrichment: the chat is fully usable without it, so no snackbar.
            // The defaults (no disappear timer, not announcement-only, not admin) are permissive;
            // the backend re-checks all three, so a failure here cannot grant real privileges.
            Log.w(TAG, "Failed to load conversation settings: ${e.message}")
        }
    }

    LaunchedEffect(conversationId) {
        val loadFailure = runCatchingCancellable {
            val result = messageRepository.getMessages(conversationId)
            messages = result.items.reversed(); nextCursor = result.nextCursor
        }.exceptionOrNull()
        // Same ordering as the pagination handler below, and for the same reason: showSnackbar
        // suspends until dismissed (~4s), and it used to sit between the failure and both
        // `isLoading = false` and the READ ack — so a failed open left the chat spinning and the
        // read receipt unsent for the life of the message.
        isLoading = false
        if (loadFailure != null) {
            Log.e(TAG, "Failed to load messages", loadFailure)
            scope.launch { snackbarHostState.showSnackbar(errorLoadMsg) }
        }
        // One receipt covers the whole conversation: the backend's READ ack bulk-marks every unread
        // message and moves `last_read_at`, so naming the newest incoming message is enough.
        messages.lastOrNull { it.senderId != currentUserId }?.let { newest ->
            sendReadReceipt(ackedMessageIds, conversationId, newest.id, send = wsClient::sendAck)
        }
    }

    // Re-assert the receipt whenever the app comes back to the front (#478).
    //
    // A chat can be open while the phone is locked. Messages arrive, the composition is never torn
    // down, so the open-handler above does not re-run and nothing else notices the user has come
    // back — the messages sit there looking read and the sender's ticks never move. `force` is the
    // point of this effect: the newest message may well be one this screen already acked, and
    // re-asserting is exactly what a socket that dropped the first attempt needs.
    LaunchedEffect(conversationId) {
        appVisibility.isForeground
            // The current value is replayed on subscribe and is not a transition; the open-handler
            // above has already covered it. Past that, a StateFlow only emits on change, so every
            // `true` here is a genuine return to the foreground.
            .drop(1)
            .filter { it }
            .collect {
                messages.lastOrNull { m -> m.senderId != currentUserId }?.let { newest ->
                    sendReadReceipt(ackedMessageIds, conversationId, newest.id, force = true, send = wsClient::sendAck)
                }
            }
    }

    // ── WebSocket listener ───────────────────
    LaunchedEffect(conversationId) {
        wsClient.incoming.collect { ws ->
            when (ws) {
                is WsMessage.NewMessage -> {
                    if (ws.conversationId == conversationId) {
                        if (messages.none { it.id == ws.messageId }) {
                            messages = messages + Message(id = ws.messageId, conversationId = ws.conversationId, senderId = ws.senderId,
                                contentType = ws.contentType, content = ws.content, replyToId = ws.replyToId, mediaUrl = ws.mediaUrl,
                                thumbnailUrl = ws.thumbnailUrl, serverTimestamp = Instant.fromEpochMilliseconds(ws.serverTimestamp),
                                clientTimestamp = Clock.System.now(), forwardedFrom = ws.forwardedFrom)
                        }
                        // Deliberately OUTSIDE the "not already rendered" guard above (#478). That
                        // guard exists to stop a bubble being drawn twice — it was also deciding
                        // whether the sender ever learns the message was read, so every path that
                        // put the message into the list first (a refresh, the SQLDelight cache, a
                        // pagination fetch) swallowed the frame and no receipt was ever sent. The
                        // brake against re-sending on every frame is ackedMessageIds, not the list.
                        if (ws.senderId != currentUserId) {
                            sendReadReceipt(ackedMessageIds, conversationId, ws.messageId, send = wsClient::sendAck)
                        }
                    }
                }
                is WsMessage.ServerAck -> {
                    if (ws.status == AckStatus.OK) {
                        messages = messages.map { m -> if (m.id == ws.messageId) m.copy(status = MessageStatus.SENT, serverTimestamp = ws.serverTimestamp?.let { Instant.fromEpochMilliseconds(it) } ?: m.serverTimestamp) else m }
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
                is WsMessage.MessageEdited -> if (ws.conversationId == conversationId) messages = messages.map { m -> if (m.id == ws.messageId) m.copy(content = ws.newContent, editedAt = Instant.fromEpochMilliseconds(ws.editedAt)) else m }
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
            // First load: jump instantly to very bottom (scrollOffset pushes item to top edge of viewport)
            listState.scrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE)
            initialScrollDone = true
        } else {
            // New message arrived: animate to bottom
            listState.animateScrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { first ->
            if (first <= 1 && nextCursor != null && !isLoadingMore && !isLoading) {
                isLoadingMore = true
                // Without feedback a failed page load is indistinguishable from "no older messages".
                val failure = runCatchingCancellable {
                    val r = messageRepository.getMessages(conversationId, cursor = nextCursor)
                    messages = r.items.reversed() + messages
                    nextCursor = r.nextCursor
                }.exceptionOrNull()
                // Release the pagination gate first, then report from a coroutine of its own.
                // This block runs inside the snapshotFlow collector: showSnackbar suspends until
                // the snackbar is dismissed (~4s), so reporting inline froze both pagination and
                // every subsequent scroll emission for the life of the message.
                isLoadingMore = false
                if (failure != null) {
                    Log.e(TAG, "Failed to load older messages", failure)
                    scope.launch { snackbarHostState.showSnackbar(errorLoadMsg) }
                }
            }
        }
    }

    val lastSeenMillis = peerLastSeen
    val subtitle = when {
        peerTyping -> typingText; peerOnline -> chatOnlineText
        lastSeenMillis != null -> "$chatLastSeenText ${formatMessageTime(Instant.fromEpochMilliseconds(lastSeenMillis))}"
        else -> null
    }

    // ── Dialogs ──────────────────────────────
    fullImageUrl?.let { url -> FullImageViewer(url) { fullImageUrl = null } }
    forwardMessage?.let { msg -> ForwardPickerDialog(msg, forwardConversations, conversationId, currentUserId, wsClient, scope, errorSendMsg, snackbarHostState, onDismiss = { forwardMessage = null }, onNavigateToConversation = onNavigateToConversation) }
    if (showDeleteDialog && deleteTargetId != null) DeleteConfirmDialog(
        onConfirm = { val id = deleteTargetId ?: return@DeleteConfirmDialog
            showDeleteDialog = false
            deleteTargetId = null
            scope.launch { try { groupRepository.deleteMessage(id)
            messages = messages.map { if (it.id == id) it.copy(isDeleted = true, content = "") else it } } catch (_: Exception) { snackbarHostState.showSnackbar(errorSendMsg) } } },
        onDismiss = { showDeleteDialog = false; deleteTargetId = null }
    )
    if (showDisappearDialog) DisappearTimerDialog(
        disappearAfterSeconds,
        onSelect = { s ->
            showDisappearDialog = false
            // Optimistic: the timer icon flips immediately. If the server rejects it we MUST roll
            // back and say so — leaving the icon "on" would claim messages disappear when they do not.
            val previous = disappearAfterSeconds
            disappearAfterSeconds = s
            scope.launch {
                runCatchingCancellable { conversationRepository.setDisappearTimer(conversationId, s) }
                    .onFailure { e ->
                        Log.e(TAG, "Failed to set disappearing timer", e)
                        disappearAfterSeconds = previous
                        snackbarHostState.showSnackbar(errorDisappearingMsg)
                    }
            }
        },
        onDismiss = { showDisappearDialog = false }
    )
    if (showLocationDialog) LocationShareDialog(onSend = { loc -> showLocationDialog = false
        val json = kotlinx.serialization.json.Json.encodeToString(LocationData.serializer(), loc)
        val mid = generateMessageId()
        val rid = generateMessageId()
        messages = messages + Message(
            id = mid,
            conversationId = conversationId,
            senderId = currentUserId,
            contentType = ContentType.LOCATION,
            content = json,
            status = MessageStatus.SENDING,
            clientTimestamp = Clock.System.now()
        )
        scope.launch { try { wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = json, contentType = ContentType.LOCATION)) } catch (e: Exception) {
        reportSendOutcome(mid, e) } } }, onDismiss = { showLocationDialog = false })
    if (showPollDialog) PollCreateDialog(onSend = { poll -> showPollDialog = false
        val json = kotlinx.serialization.json.Json.encodeToString(PollData.serializer(), poll)
        val mid = generateMessageId()
        val rid = generateMessageId()
        messages = messages + Message(
            id = mid,
            conversationId = conversationId,
            senderId = currentUserId,
            contentType = ContentType.POLL,
            content = json,
            status = MessageStatus.SENDING,
            clientTimestamp = Clock.System.now()
        )
        scope.launch { try { wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = json, contentType = ContentType.POLL)) } catch (e: Exception) {
        reportSendOutcome(mid, e) } } }, onDismiss = { showPollDialog = false })

    // Scheduled send: pick date+time, then reuse the existing send path with scheduledAt set.
    if (showScheduleDialog) ScheduleSendDialog(
        onConfirm = { epochMillis ->
            showScheduleDialog = false
            val text = messageText.trim()
            if (text.isEmpty()) return@ScheduleSendDialog
            val replyId = replyingTo?.id
            messageText = ""; replyingTo = null; typingJob?.cancel()
            if (isTypingSent) { scope.launch { sendTypingIndicator(wsClient, conversationId, false) }; isTypingSent = false }
            val mid = generateMessageId(); val rid = generateMessageId()
            pendingScheduled = pendingScheduled + PendingScheduledMessage(messageId = mid, content = text, scheduledAtMillis = epochMillis)
            scope.launch {
                try {
                    wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = text, contentType = ContentType.TEXT, replyToId = replyId, scheduledAt = epochMillis))
                    snackbarHostState.showSnackbar(scheduleQueuedMsg)
                } catch (_: Exception) {
                    pendingScheduled = pendingScheduled.filter { it.messageId != mid }
                    snackbarHostState.showSnackbar(errorSendMsg)
                }
            }
        },
        onDismiss = { showScheduleDialog = false }
    )

    // View / cancel pending scheduled messages. Cancel reuses the existing delete-message endpoint.
    if (showScheduledListDialog) ScheduledMessagesDialog(
        pending = pendingScheduled,
        onCancelScheduled = { item ->
            pendingScheduled = pendingScheduled.filter { it.messageId != item.messageId }
            if (pendingScheduled.isEmpty()) showScheduledListDialog = false
            scope.launch { try { groupRepository.deleteMessage(item.messageId)
                snackbarHostState.showSnackbar(scheduleCancelledMsg) } catch (_: Exception) { snackbarHostState.showSnackbar(errorSendMsg) } }
        },
        onDismiss = { showScheduledListDialog = false }
    )

    // ── Scaffold UI ──────────────────────────
    MuhabbetScaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Avatar in the title, not just a name. Every messenger worth comparing against
                    // shows the person you are talking to at the top of the conversation, and its
                    // absence here was the single most "unfinished" thing left on this screen. The
                    // whole row is the tap target for the profile, so the avatar is not a separate
                    // affordance to discover.
                    Row(
                        modifier = Modifier.clickable { onTitleClick() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            avatarUrl = conversationAvatarUrl,
                            displayName = conversationName,
                            size = Muhabbet.sizes.AvatarSmall,
                            isGroup = isGroup,
                            contentDescription = if (isGroup) groupAvatarLabel else null,
                            modifier = Modifier.handoffAvatar(conversationId, isChatSide = true)
                        )
                        Spacer(Modifier.width(Muhabbet.spacing.Small))
                        Column {
                            Text(conversationName)
                            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = LocalSemanticColors.current.secondaryText)
                        }
                    }
                },
                navigationIcon = { MuhabbetIconButton(
                                       icon = Muhabbet.icons.Back,
                                       contentDescription = stringResource(Res.string.action_back),
                                       onClick = onBack
                                   ) },
                actions = { MuhabbetIconButton(
                                icon = if (disappearAfterSeconds != null) Muhabbet.icons.Timer else Muhabbet.icons.TimerOff,
                                contentDescription = stringResource(Res.string.chat_disappearing),
                                onClick = { showDisappearDialog = true }
                            ) },
                // Bespoke bar (avatar + name + presence subtitle), shared colours.
                colors = MuhabbetTopBarDefaults.colors()
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            // Above the messages and below the bar, so it is the first thing read on a screen the
            // user is about to type into. Self-hiding, and silent for the first few seconds of any
            // outage — see ConnectionStrip.
            ConnectionStrip(state = connectionState)
            if (isLoading) {
                MuhabbetLoadingState(Modifier.weight(1f).fillMaxWidth())
            } else {
                ChatMessageList(
                    messages = messages,
                    currentUserId = currentUserId,
                    starredIds = starredIds.value,
                    audioPlayer = audioPlayer,
                    isLoadingMore = isLoadingMore,
                    peerTyping = peerTyping,
                    contextMenuMessageId = contextMenuMessageId,
                    listState = listState,
                    scope = scope,
                    modifier = Modifier.weight(1f),
                    actions = ChatMessageActions(
                        onSwipeReply = { replyingTo = it },
                        onLongPress = { contextMenuMessageId = it.id },
                        onDismissMenu = { contextMenuMessageId = null },
                        onReply = { contextMenuMessageId = null; replyingTo = it },
                        // Without feedback the picker just opens empty, reading as "no chats to forward to".
                        onForward = { msg -> contextMenuMessageId = null
                            forwardMessage = msg
                            scope.launch { runCatchingCancellable { forwardConversations = conversationRepository.getConversations().items }.onFailure { e -> Log.e(TAG, "Failed to load forward targets", e)
                            snackbarHostState.showSnackbar(errorLoadConversationsMsg) } } },
                        onStar = { msg, isStarred -> contextMenuMessageId = null
                            scope.launch { runCatchingCancellable { if (isStarred) { messageRepository.unstarMessage(msg.id)
                            starredIds.value -= msg.id } else { messageRepository.starMessage(msg.id)
                            starredIds.value += msg.id } }.onFailure { e -> Log.e(TAG, "Failed to toggle star on ${msg.id}", e)
                            snackbarHostState.showSnackbar(errorActionMsg) } } },
                        onEdit = { msg -> contextMenuMessageId = null; editingMessageId = msg.id; messageText = msg.content },
                        onDelete = { msg -> contextMenuMessageId = null; deleteTargetId = msg.id; showDeleteDialog = true },
                        onImageClick = { fullImageUrl = it },
                        onReactionToggle = { msg, emoji ->
                            scope.launch {
                                runCatchingCancellable {
                                    if (emoji in msg.myReactions) messageRepository.removeReaction(msg.id, emoji)
                                    else messageRepository.addReaction(msg.id, emoji)
                                }.onFailure { e ->
                                    Log.e(TAG, "Failed to toggle reaction on ${msg.id}", e)
                                    snackbarHostState.showSnackbar(errorActionMsg)
                                }
                            }
                        },
                        onQuickReaction = { msg, emoji -> scope.launch { runCatchingCancellable { messageRepository.addReaction(msg.id, emoji) }.onFailure { e -> Log.e(TAG, "Failed to add reaction to ${msg.id}", e)
                            snackbarHostState.showSnackbar(errorActionMsg) } } },
                        onInfo = { msg -> contextMenuMessageId = null; onMessageInfo?.invoke(msg.id) },
                        // Server-side bookkeeping only — the media is already revealed locally, so a
                        // failure has no user-visible consequence worth interrupting them for.
                        onViewOnce = { id -> scope.launch { runCatchingCancellable { messageRepository.markViewOnce(id) }.onFailure { e -> Log.w(TAG, "Failed to mark view-once $id as viewed: ${e.message}") } } },
                        onOpenUrl = { url -> openExternally(url) },
                        // The bubble drew a video it has no playable url for. Saying so is the
                        // whole point — a tap that does nothing is the defect being fixed.
                        onMediaUnavailable = {
                            scope.launch { snackbarHostState.showSnackbar(errorVideoUnavailableMsg) }
                        }
                    )
                )
            }

            // Pending scheduled messages chip (session-local)
            if (pendingScheduled.isNotEmpty()) {
                ScheduledMessagesChip(count = pendingScheduled.size, onClick = { showScheduledListDialog = true })
            }

            // Reply / Edit bars
            replyingTo?.let { ReplyPreviewBar(it) { replyingTo = null } }
            if (editingMessageId != null) EditModeBar(chatEditMode) { editingMessageId = null; messageText = "" }

            // Voice recording or input bar
            if (isRecording) {
                Surface(tonalElevation = MuhabbetElevation.Level2) {
                    Row(Modifier.fillMaxWidth().padding(MuhabbetSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                        VoiceRecordButton(true, {}, onStopRecording = {
                            val audio = audioRecorder.stopRecording(); isRecording = false
                            if (audio != null) scope.launch {
                                isUploading = true
                                var sendFailed = false
                                try {
                                    val upload = mediaUploadHelper.uploadAudio(
                                        audio.bytes,
                                        "voice_${Clock.System.now().toEpochMilliseconds()}.ogg",
                                        audio.mimeType,
                                        audio.durationSeconds
                                    )
                                    val mid = generateMessageId(); val rid = generateMessageId()
                                    messages = messages + Message(
                                        id = mid,
                                        conversationId = conversationId,
                                        senderId = currentUserId,
                                        contentType = ContentType.VOICE,
                                        content = chatVoiceText,
                                        mediaUrl = upload.url,
                                        status = MessageStatus.SENDING,
                                        clientTimestamp = Clock.System.now()
                                    )
                                    wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = chatVoiceText, contentType = ContentType.VOICE, mediaUrl = upload.url))
                                } catch (_: Exception) { sendFailed = true }
                                // Clear the spinner BEFORE reporting — showSnackbar suspends until
                                // dismissed (~4s).
                                isUploading = false
                                if (sendFailed) snackbarHostState.showSnackbar(errorSendMsg)
                            }
                        }, onCancelRecording = { audioRecorder.cancelRecording(); isRecording = false }, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                val inputEnabled = !isAnnouncementOnly || isAdminOrOwner

                if (!inputEnabled) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        tonalElevation = MuhabbetElevation.None
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(MuhabbetSpacing.Large),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.announcement_mode),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else MessageInputBar(messageText,
                    onTextChange = { new ->
                        messageText = new
                        if (new.isNotEmpty() && editingMessageId == null) {
                            if (!isTypingSent) { scope.launch { sendTypingIndicator(wsClient, conversationId, true) }; isTypingSent = true }
                            typingJob?.cancel()
                                typingJob = scope.launch { delay(com.muhabbet.designsystem.theme.MuhabbetDurations.TypingTimeoutMs)
                                sendTypingIndicator(wsClient, conversationId, false)
                                isTypingSent = false }
                        }
                    },
                    isEditing = editingMessageId != null, isUploading = isUploading,
                    onSend = {
                        if (messageText.isBlank()) return@MessageInputBar
                        if (editingMessageId != null) {
                            val id = editingMessageId ?: return@MessageInputBar; val content = messageText.trim(); editingMessageId = null; messageText = ""
                            scope.launch { try { groupRepository.editMessage(id, content)
                                messages = messages.map { if (it.id == id) it.copy(content = content, editedAt = Clock.System.now()) else it } } catch (_: Exception) { snackbarHostState.showSnackbar(errorSendMsg) } }
                        } else {
                            val text = messageText; val replyId = replyingTo?.id; messageText = ""; replyingTo = null; typingJob?.cancel()
                            if (isTypingSent) { scope.launch { sendTypingIndicator(wsClient, conversationId, false) }; isTypingSent = false }
                            val mid = generateMessageId(); val rid = generateMessageId()
                            messages = messages + Message(
                                id = mid,
                                conversationId = conversationId,
                                senderId = currentUserId,
                                contentType = ContentType.TEXT,
                                content = text,
                                replyToId = replyId,
                                status = MessageStatus.SENDING,
                                clientTimestamp = Clock.System.now()
                            )
                            scope.launch { try { wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = text, contentType = ContentType.TEXT, replyToId = replyId)) } catch (e: Exception) {
                                reportSendOutcome(mid, e) } }
                        }
                    },
                    onMicClick = { if (audioRecorder.hasPermission()) { audioRecorder.startRecording(); isRecording = true } else requestAudioPermission() },
                    onImagePick = { imagePickerLauncher.launch() }, onFilePick = { filePickerLauncher.launch() },
                    onPollCreate = { showPollDialog = true }, onLocationShare = { showLocationDialog = true },
                    onGifPick = { gifPickerTab = GifStickerTab.GIF },
                    onStickerPick = { gifPickerTab = GifStickerTab.STICKER },
                    onCameraPick = { cameraPickerLauncher.launch() },
                    viewOnceEnabled = viewOnceEnabled,
                    onViewOnceToggle = { viewOnceEnabled = !viewOnceEnabled },
                    onScheduleSend = { if (messageText.isNotBlank()) showScheduleDialog = true }
                )
            }
        }
    }

    // GIF/Sticker picker
    gifPickerTab?.let { openTab ->
        GifStickerPicker(
            initialTab = openTab,
            onDismiss = { gifPickerTab = null },
            onGifSelected = { url, _ ->
                gifPickerTab = null
                val mid = generateMessageId()
                val rid = generateMessageId()
                messages = messages + Message(
                    id = mid,
                    conversationId = conversationId,
                    senderId = currentUserId,
                    contentType = ContentType.GIF,
                    content = gifContentLabel,
                    mediaUrl = url,
                    status = MessageStatus.SENDING,
                    clientTimestamp = Clock.System.now()
                )
                scope.launch { try { wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = gifContentLabel, contentType = ContentType.GIF, mediaUrl = url)) } catch (e: Exception) {
                    reportSendOutcome(mid, e) } }
            },
            onStickerSelected = { url, _ ->
                gifPickerTab = null
                val mid = generateMessageId()
                val rid = generateMessageId()
                messages = messages + Message(
                    id = mid,
                    conversationId = conversationId,
                    senderId = currentUserId,
                    contentType = ContentType.STICKER,
                    content = stickerContentLabel,
                    mediaUrl = url,
                    status = MessageStatus.SENDING,
                    clientTimestamp = Clock.System.now()
                )
                scope.launch { try { wsClient.send(WsMessage.SendMessage(requestId = rid, messageId = mid, conversationId = conversationId, content = stickerContentLabel, contentType = ContentType.STICKER, mediaUrl = url)) } catch (e: Exception) {
                    reportSendOutcome(mid, e) } }
            }
        )
    }
}

/**
 * Typing indicators are cosmetic and fire on every typing burst. A failure is almost always just a
 * dropped WebSocket, and interrupting the user mid-sentence for it would be worse than useless — so
 * this deliberately stays silent in the UI and only logs.
 */
private suspend fun sendTypingIndicator(wsClient: WsClient, conversationId: String, isTyping: Boolean) {
    runCatchingCancellable { wsClient.send(WsMessage.TypingIndicator(conversationId, isTyping)) }
        .onFailure { e -> Log.d(TAG, "Typing indicator ($isTyping) not sent: ${e.message}") }
}
