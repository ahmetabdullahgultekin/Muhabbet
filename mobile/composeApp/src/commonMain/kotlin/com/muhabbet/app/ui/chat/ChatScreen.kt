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
import com.muhabbet.app.data.remote.ApiException
import com.muhabbet.app.data.remote.MessageQueuedException
import com.muhabbet.app.data.remote.ViewOnceNotQueueableException
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.ui.connection.ConnectionStrip
import com.muhabbet.app.ui.conversations.ChatTarget
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.data.repository.MediaUploadHelper
import com.muhabbet.app.data.repository.MessageRepository
import com.muhabbet.app.platform.AppVisibility
import com.muhabbet.app.platform.RecordedAudio
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
import com.muhabbet.designsystem.components.MuhabbetSkeletonConversation
import com.muhabbet.designsystem.components.MuhabbetSkeletonGate

private const val TAG = "ChatScreen"

/**
 * `ErrorCode.MSG_VIEW_ONCE_ALREADY_VIEWED` as it arrives in the envelope's `error.code`.
 *
 * Matched on the code rather than the message: the message is Turkish prose that a backend deploy
 * may reword at any time, and the difference being decided here — "someone already opened this" vs
 * "the request failed" — is the difference between a correct refusal and a bug report.
 */
private const val ViewOnceAlreadyViewedCode = "MSG_VIEW_ONCE_ALREADY_VIEWED"

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
    onNavigateToConversation: ((ChatTarget) -> Unit)? = null,
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
    val viewOnceOfflineMsg = stringResource(Res.string.view_once_offline)
    val viewOnceAlreadyOpenedMsg = stringResource(Res.string.view_once_already_opened)
    val viewOnceOpenFailedMsg = stringResource(Res.string.view_once_open_failed)
    // Spoken once when the placeholder bubbles appear; the bubbles themselves are silent.
    val loadingMessagesLabel = stringResource(Res.string.messages_loading)

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

    // Voice recording — see VoiceRecordingPhase for the state machine. Before #601 there were only
    // two states, recording and not, and the only way out of the first was tapping the same button
    // again, which stopped the recording AND sent it in one motion — no cancel, no preview, no way
    // to keep talking past the length of one held press.
    val audioRecorder = rememberAudioRecorder()
    val audioPlayer = rememberAudioPlayer()
    var recordingPhase by remember { mutableStateOf<VoiceRecordingPhase>(VoiceRecordingPhase.Idle) }
    var recordingSeconds by remember { mutableStateOf(0) }
    // Held and Locked both keep the recorder running; Preview shows a fixed duration read off the
    // finished file, not a live counter, so it is deliberately excluded here.
    val isRecordingLive = recordingPhase is VoiceRecordingPhase.Held || recordingPhase is VoiceRecordingPhase.Locked
    LaunchedEffect(isRecordingLive) {
        if (isRecordingLive) {
            recordingSeconds = 0
            while (true) { delay(1000); recordingSeconds++ }
        }
    }
    // The permission prompt is fired from onRecordPressStart below when it is still missing; there
    // is nothing useful to auto-start here once it resolves, because the grant lands well after the
    // press that triggered it has already ended without recording anything. The next press starts
    // recording normally.
    val requestAudioPermission = rememberAudioPermissionRequester { }

    suspend fun sendRecordedAudio(audio: RecordedAudio) {
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
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isUploading = false
        if (sendFailed) snackbarHostState.showSnackbar(errorSendMsg)
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

    // Gallery and camera both land here. One handler, because they only ever differed in which
    // picker produced the bytes, and two copies of a privacy flag is how one of them gets it and the
    // other does not (#515).
    suspend fun sendPhoto(bytes: ByteArray, fileName: String) {
        isUploading = true
        val armed = viewOnceEnabled
        var sendError: Throwable? = null
        val msgId = generateMessageId()
        try {
            val upload = mediaUploadHelper.uploadImage(bytes, fileName)
            val photo = outgoingPhoto(
                messageId = msgId,
                requestId = generateMessageId(),
                conversationId = conversationId,
                senderId = currentUserId,
                caption = chatPhotoText,
                mediaUrl = upload.url,
                thumbnailUrl = upload.thumbnailUrl,
                viewOnce = armed,
                sentAt = Clock.System.now()
            )
            messages = messages + photo.optimistic
            wsClient.send(photo.frame)
        } catch (e: Exception) { sendError = e }
        // Disarmed on the way out of the attempt, not inside the `try`: `wsClient.send` throws
        // when the socket is down, so clearing it there left the flag set after a failure with
        // the sheet closed — armed, invisible, and applied to whatever photo came next.
        viewOnceEnabled = false
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isUploading = false
        when (sendError) {
            null -> Unit
            // The one send outcome that is neither "sent" nor "will be sent": a view-once photo is
            // refused rather than queued, because the offline queue cannot carry the flag and would
            // deliver it unsealed on the next reconnect. Say so specifically — "could not send"
            // over a socket that is merely down invites the user to retry immediately and fail
            // again, and gives no hint that the setting is what made this send different.
            is ViewOnceNotQueueableException -> {
                messages = messages.filter { it.id != msgId }
                snackbarHostState.showSnackbar(viewOnceOfflineMsg)
            }
            else -> reportSendOutcome(msgId, sendError)
        }
    }

    val imagePickerLauncher = rememberImagePickerLauncher { picked ->
        if (picked == null) return@rememberImagePickerLauncher
        scope.launch { sendPhoto(picked.bytes, picked.fileName) }
    }

    // Camera picker
    val cameraPickerLauncher = rememberCameraPickerLauncher { picked ->
        if (picked == null) return@rememberCameraPickerLauncher
        scope.launch { sendPhoto(picked.bytes, picked.fileName) }
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

            // Seed the header subtitle (#644). `peerOnline`/`peerLastSeen` were previously only
            // ever written by the WebSocket listener below, reacting to a PresenceUpdate frame —
            // nothing ever asked for the peer's current status, so a chat opened onto a peer who
            // was already online (or already offline with a last-seen from an hour ago) showed a
            // blank subtitle until their presence happened to change while this screen was open.
            // GET /users/{id} already applies the peer's own onlineStatusVisibility server-side
            // (UserController.resolveVisibility, #377) — a peer set to "nobody" correctly returns
            // isOnline=false/lastSeenAt=null here too, so this seeds exactly what that setting
            // allows and nothing more. Only meaningful for a 1:1 chat: a group has no single peer.
            if (!isGroup) {
                val peerId = conv?.participants?.firstOrNull { it.userId != currentUserId }?.userId
                if (peerId != null) {
                    val profile = conversationRepository.getUserProfile(peerId)
                    peerOnline = profile.isOnline
                    peerLastSeen = profile.lastSeenAt?.toEpochMilliseconds()
                }
            }
        }.onFailure { e ->
            // Best-effort enrichment: the chat is fully usable without it, so no snackbar.
            // The defaults (no disappear timer, not announcement-only, not admin, blank presence
            // subtitle) are permissive; the backend re-checks all three privilege flags, so a
            // failure here cannot grant real privileges, and a missing subtitle is silent, not
            // wrong.
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
                                clientTimestamp = Clock.System.now(), forwardedFrom = ws.forwardedFrom,
                                // Without this the live path could not seal anything: the recipient
                                // builds their bubble from this frame, so a photo that arrives while
                                // the chat is open rendered in full no matter what the sender chose.
                                // The reload path was equally blind — see MessageMapper (#515).
                                viewOnce = ws.viewOnce)
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
                    if (ws.conversationId == conversationId) {
                        if (ws.status == PresenceStatus.TYPING) {
                            peerTyping = true
                            typingDismissJob?.cancel()
                            typingDismissJob = scope.launch {
                                delay(com.muhabbet.designsystem.theme.MuhabbetDurations.TypingTimeoutMs)
                                peerTyping = false
                            }
                        } else {
                            // handleTypingIndicator (backend) sends this same conversationId with
                            // PresenceStatus.ONLINE when the peer's `isTyping` frame was `false` —
                            // the explicit "stopped typing" signal. Without handling it the bubble
                            // depended entirely on the fallback timer above and could linger for up
                            // to TypingTimeoutMs after the peer had already stopped.
                            typingDismissJob?.cancel()
                            peerTyping = false
                        }
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

    // The other half of #643. `ChatMessageList` appends the typing bubble as its own trailing
    // item (`ChatMessageList.kt`, key = "typing"), separate from `messages`, so the effect above —
    // keyed on `messages.size` — never reruns when `peerTyping` flips. A reader who was already
    // scrolled to the newest message (the common case: they are looking at the chat because
    // someone is about to reply) had the bubble land one row below their viewport with nothing to
    // bring it into view — composed, correct, and never seen, exactly as reported. Scrolling is
    // gated on already being at (or within two rows of) the bottom: someone reading older history
    // must not be yanked back down just because the peer started typing.
    LaunchedEffect(peerTyping) {
        if (!peerTyping || messages.isEmpty()) return@LaunchedEffect
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        val totalItems = listState.layoutInfo.totalItemsCount
        if (lastVisible >= totalItems - 2) {
            listState.animateScrollToItem(messages.lastIndex, scrollOffset = Int.MAX_VALUE)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { first ->
            // initialScrollDone, not just !isLoading: the very first layout pass of a freshly
            // mounted LazyColumn can report firstVisibleItemIndex = 0 for a frame before the
            // jump-to-bottom effect above has actually applied its scroll — isLoading is already
            // false by then, since both flip in the same state batch. Reading that transient 0 as
            // "the user scrolled to the top" fired pagination on a conversation the user had not
            // even looked at yet, prepending an older page while the initial jump was still
            // in flight and turning "open at the bottom" into "open in the middle" (#590), and only
            // for conversations long enough to have a nextCursor at all. initialScrollDone flips
            // true in the same effect that issues the jump, so this gate cannot open before that
            // jump has at least been requested.
            if (first <= 1 && initialScrollDone && nextCursor != null && !isLoadingMore && !isLoading) {
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
                    // row opens the profile; the avatar itself is carved out below to open full-screen
                    // instead (#615).
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
                            // A separate tap zone nested inside the title Row's own onTitleClick: a
                            // tap on the avatar opens it full-screen (reusing the same viewer and
                            // dialog state a chat photo uses — #615) and continues the shared-element
                            // handoff below rather than cutting; a tap anywhere else in the row still
                            // opens the profile via onTitleClick. No photo means no navigation — the
                            // name-seeded gradient fallback isn't worth a full-screen view.
                            modifier = Modifier
                                .then(
                                    if (conversationAvatarUrl != null) {
                                        Modifier.clickable { fullImageUrl = conversationAvatarUrl }
                                    } else {
                                        Modifier
                                    }
                                )
                                .handoffAvatar(conversationId, isChatSide = true)
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
            // The wallpaper sits outside the gate, so the skeleton and the messages that replace it
            // are painted on the same backdrop and the swap is invisible.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ChatWallpaper()
                MuhabbetSkeletonGate(
                    isLoading = isLoading,
                    skeleton = {
                        MuhabbetSkeletonConversation(loadingLabel = loadingMessagesLabel)
                    }
                ) {
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
                        modifier = Modifier.fillMaxSize(),
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
                            // Not bookkeeping — this call *is* the view.
                            //
                            // It used to be a fire-and-forget POST whose failure was logged and
                            // swallowed, on the reasoning that "the media is already revealed locally".
                            // It never was: the sealed bubble had no reveal step at all, so a recipient
                            // could burn a view-once photo and never see it. And it could not have been,
                            // because the flag never arrived, so no recipient ever rendered this bubble.
                            //
                            // Now the server holds the only copy of the URL and releases it in the same
                            // transaction that burns the message. A failure therefore matters: it means
                            // the photo was not shown, and the user has to be told which of the two
                            // reasons applies — already opened (from another device, or a second tap
                            // that lost the race) or a genuine failure.
                            onViewOnce = { id ->
                                scope.launch {
                                    // Sealed optimistically, so a second tap during the round trip
                                    // cannot fire a second reveal at a message that has one.
                                    messages = messages.map {
                                        if (it.id == id) it.copy(viewOnceViewed = true) else it
                                    }
                                    runCatchingCancellable { messageRepository.revealViewOnce(id) }
                                        .onSuccess { reveal -> reveal.mediaUrl?.let { fullImageUrl = it } }
                                        .onFailure { e ->
                                            Log.w(TAG, "Failed to open view-once $id: ${e.message}")
                                            val alreadyViewed =
                                                (e as? ApiException)?.code == ViewOnceAlreadyViewedCode
                                            // A refusal means it really is spent, so the seal stays.
                                            // Anything else — the request never landed — must not cost
                                            // the user their one look; put the seal back so they can
                                            // open it when the network returns. If the burn did succeed
                                            // and only the reply was lost, the retry says "already
                                            // opened", which is the truth and the best available answer.
                                            if (!alreadyViewed) {
                                                messages = messages.map {
                                                    if (it.id == id) it.copy(viewOnceViewed = false) else it
                                                }
                                            }
                                            snackbarHostState.showSnackbar(
                                                if (alreadyViewed) viewOnceAlreadyOpenedMsg else viewOnceOpenFailedMsg
                                            )
                                        }
                                }
                            },
                            onOpenUrl = { url -> openExternally(url) },
                            // The bubble drew a video it has no playable url for. Saying so is the
                            // whole point — a tap that does nothing is the defect being fixed.
                            onMediaUnavailable = {
                                scope.launch { snackbarHostState.showSnackbar(errorVideoUnavailableMsg) }
                            }
                        )
                    )
                }
            }

            // Pending scheduled messages chip (session-local)
            if (pendingScheduled.isNotEmpty()) {
                ScheduledMessagesChip(count = pendingScheduled.size, onClick = { showScheduledListDialog = true })
            }

            // Reply / Edit bars
            replyingTo?.let { ReplyPreviewBar(it) { replyingTo = null } }
            if (editingMessageId != null) EditModeBar(chatEditMode) { editingMessageId = null; messageText = "" }

            // Voice recording or input bar.
            //
            // Locked and Preview each get their own bar, swapped in exactly as freely as the old
            // isRecording flag used to — there is no live gesture to lose once either is reached
            // (the finger is already up in both). Idle and Held are the case that isn't free to
            // swap; both go through the same MessageInputBar call so its VoiceRecordGestureButton
            // stays mounted across that specific transition. See that composable's doc for why.
            when (val phase = recordingPhase) {
                is VoiceRecordingPhase.Locked -> LockedRecordingBar(
                    recordingSeconds = recordingSeconds,
                    onCancel = {
                        audioRecorder.cancelRecording()
                        recordingPhase = VoiceRecordingPhase.Idle
                    },
                    onSend = {
                        val audio = audioRecorder.stopRecording()
                        recordingPhase = VoiceRecordingPhase.Idle
                        if (audio != null) scope.launch {
                            sendRecordedAudio(audio)
                            audioRecorder.discardPreview()
                        }
                    }
                )
                is VoiceRecordingPhase.Preview -> VoicePreviewBar(
                    audio = phase.audio,
                    audioPlayer = audioPlayer,
                    onDiscard = {
                        audioPlayer.stop()
                        audioRecorder.discardPreview()
                        recordingPhase = VoiceRecordingPhase.Idle
                    },
                    onSend = {
                        audioPlayer.stop()
                        val audio = phase.audio
                        recordingPhase = VoiceRecordingPhase.Idle
                        scope.launch {
                            sendRecordedAudio(audio)
                            audioRecorder.discardPreview()
                        }
                    }
                )
                else -> {
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
                    recordingPhase = phase,
                    recordingSeconds = recordingSeconds,
                    onRecordPressStart = {
                        if (audioRecorder.hasPermission()) {
                            audioRecorder.startRecording()
                            recordingPhase = VoiceRecordingPhase.Held(0f, 0f)
                            true
                        } else {
                            requestAudioPermission()
                            false
                        }
                    },
                    onRecordDragUpdate = { dx, dy -> recordingPhase = VoiceRecordingPhase.Held(dx, dy) },
                    onRecordLocked = { recordingPhase = VoiceRecordingPhase.Locked },
                    onRecordReleased = { dx, _ ->
                        if (isVoiceRecordingCancelledAt(dx)) {
                            audioRecorder.cancelRecording()
                            recordingPhase = VoiceRecordingPhase.Idle
                        } else {
                            val audio = audioRecorder.stopRecording()
                            recordingPhase = if (audio != null) VoiceRecordingPhase.Preview(audio) else VoiceRecordingPhase.Idle
                        }
                    },
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
