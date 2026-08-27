package com.muhabbet.app.ui.status

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.MediaUploadHelper
import com.muhabbet.app.data.repository.StatusRepository
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.app.platform.rememberImagePickerLauncher
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.SectionHeader
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetCorners
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.ui.contacts.rememberContactNames
import com.muhabbet.app.ui.conversations.resolveName
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.action_retry
import com.muhabbet.composeapp.generated.resources.cancel
import com.muhabbet.composeapp.generated.resources.settings_title
import com.muhabbet.composeapp.generated.resources.status_add
import com.muhabbet.composeapp.generated.resources.status_add_photo
import com.muhabbet.composeapp.generated.resources.status_create_title
import com.muhabbet.composeapp.generated.resources.status_load_failed
import com.muhabbet.composeapp.generated.resources.status_my
import com.muhabbet.composeapp.generated.resources.status_no_statuses
import com.muhabbet.composeapp.generated.resources.status_placeholder
import com.muhabbet.composeapp.generated.resources.status_post
import com.muhabbet.composeapp.generated.resources.status_post_failed
import com.muhabbet.composeapp.generated.resources.unknown_person
import com.muhabbet.composeapp.generated.resources.updates_recent
import com.muhabbet.composeapp.generated.resources.updates_status_meta
import com.muhabbet.composeapp.generated.resources.updates_title
import com.muhabbet.shared.dto.UserStatusGroup
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetDialog
import com.muhabbet.designsystem.components.MuhabbetErrorState
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.theme.containerColor
import com.muhabbet.designsystem.theme.depth
import com.muhabbet.designsystem.theme.MuhabbetDepth
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.modifier.pressable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesTabScreen(
    onStatusClick: (userId: String, displayName: String) -> Unit,
    onSettings: () -> Unit,
    refreshKey: Int = 0,
    showTopBar: Boolean = true,
    statusRepository: StatusRepository = koinInject(),
    conversationRepository: ConversationRepository = koinInject(),
    mediaUploadHelper: MediaUploadHelper = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var statusGroups by remember { mutableStateOf<List<UserStatusGroup>>(emptyList()) }
    var displayNameByUserId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var avatarByUserId by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showStatusInput by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var statusPickedImage by remember { mutableStateOf<PickedImage?>(null) }
    var isUploadingStatus by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val currentUserId = remember { tokenStorage.getUserId() }
    val statusImagePicker = rememberImagePickerLauncher { image ->
        statusPickedImage = image
    }

    val updatesTitle = stringResource(Res.string.updates_title)
    val updatesRecent = stringResource(Res.string.updates_recent)
    val myStatus = stringResource(Res.string.status_my)
    val statusAdd = stringResource(Res.string.status_add)
    val statusCreateTitle = stringResource(Res.string.status_create_title)
    val statusPlaceholder = stringResource(Res.string.status_placeholder)
    val statusPost = stringResource(Res.string.status_post)
    val statusAddPhoto = stringResource(Res.string.status_add_photo)
    val cancelText = stringResource(Res.string.cancel)
    val noStatuses = stringResource(Res.string.status_no_statuses)
    val loadFailed = stringResource(Res.string.status_load_failed)
    val statusPostFailed = stringResource(Res.string.status_post_failed)
    val retryLabel = stringResource(Res.string.action_retry)
    val settingsTitle = stringResource(Res.string.settings_title)
    val unknownPersonLabel = stringResource(Res.string.unknown_person)
    val contactNames = rememberContactNames()

    suspend fun loadUpdates() {
        isLoading = true
        errorMessage = null
        runCatchingCancellable {
            statusGroups = statusRepository.getContactStatuses()
                .filter { it.statuses.isNotEmpty() }
                .sortedByDescending { group ->
                    group.statuses.maxOfOrNull { it.createdAt } ?: 0L
                }
        }.onFailure { e ->
            Log.e(TAG, "Failed to load contact statuses", e)
            errorMessage = loadFailed
        }

        // Separate from the load above, and deliberately absorbed. This call only refines names and
        // avatars for statuses that have already arrived; sharing one catch meant a failure here
        // replaced a perfectly good Updates tab with a full-screen "statuses could not be loaded".
        // Since #507 the server sends the author's name on the group itself, so a failure here
        // costs nothing at all — it only means a locally-known name does not override it.
        runCatchingCancellable {
            val participants = conversationRepository.getConversations().items
                .flatMap { it.participants }
                .associateBy { it.userId }

            // No user-id fallback: a participant we cannot name contributes no entry, so the
            // caller falls through to the server's name and then to a plain "unknown contact".
            displayNameByUserId = participants.mapNotNull { (id, participant) ->
                // The order lives in ParticipantResponse.resolveName. Spelled out here it lacked
                // the address-book rung, so a status author you have saved under a name of your
                // own showed up as their phone number (#549).
                participant.resolveName(contactNames)?.let { id to it }
            }.toMap()
            avatarByUserId = participants.mapValues { (_, participant) -> participant.avatarUrl }
        }.onFailure { e -> Log.w(TAG, "Status author names unavailable: ${e.message}") }
        isLoading = false
    }

    LaunchedEffect(refreshKey) {
        loadUpdates()
    }

    if (showStatusInput) {
        // One dismissal path instead of two: the scrim tap and the Cancel button ran identical
        // bodies, and `dismissible` now guards both with the same condition. Previously only the
        // scrim tap and the button's `enabled` were guarded — the back gesture was not, so an
        // upload in flight could be cancelled out from under itself.
        val dismissStatusInput = {
            showStatusInput = false
            statusText = ""
            statusPickedImage = null
        }
        MuhabbetDialog(
            onDismiss = dismissStatusInput,
            title = statusCreateTitle,
            dismissLabel = cancelText,
            confirmLabel = statusPost,
            confirmEnabled = (statusText.isNotBlank() || statusPickedImage != null) && !isUploadingStatus,
            dismissible = !isUploadingStatus,
            onConfirm = {
                val text = statusText.trim()
                if (text.isNotEmpty() || statusPickedImage != null) {
                    isUploadingStatus = true
                    scope.launch {
                        try {
                            var mediaUrl: String? = null
                            statusPickedImage?.let { img ->
                                val upload = mediaUploadHelper.uploadImage(img.bytes, img.fileName)
                                mediaUrl = upload.url
                            }
                            statusRepository.createStatus(
                                content = text.ifEmpty { null },
                                mediaUrl = mediaUrl
                            )
                            loadUpdates()
                            dismissStatusInput()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to post status", e)
                            errorMessage = statusPostFailed
                        }
                        isUploadingStatus = false
                    }
                }
            },
            content = {
                Column {
                    MuhabbetTextField(
                        value = statusText,
                        onValueChange = { statusText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = statusPlaceholder,
                        singleLine = false,
                        maxLines = 3
                    )
                    Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { statusImagePicker.launch() },
                            enabled = !isUploadingStatus
                        ) {
                            Icon(
                                imageVector = Muhabbet.icons.Add,
                                contentDescription = statusAddPhoto,
                                modifier = Modifier.size(MuhabbetSizes.IconSmall)
                            )
                            Spacer(Modifier.width(MuhabbetSpacing.XSmall))
                            Text(statusAddPhoto)
                        }
                        if (statusPickedImage != null) {
                            Text(
                                text = statusPickedImage?.fileName.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (isUploadingStatus) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(MuhabbetSizes.IconLarge)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        )
    }

    MuhabbetScaffold(
        topBar = {
            if (showTopBar) {
                MuhabbetTopBar(
                    title = updatesTitle,
                    actions = {
                        MuhabbetIconButton(
                            icon = Muhabbet.icons.Settings,
                            contentDescription = settingsTitle,
                            onClick = onSettings
                        )
                    }
                )
            }
        }
    ) { padding ->
        when {
            isLoading -> {
                MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
            }
            errorMessage != null -> MuhabbetErrorState(
                message = errorMessage ?: loadFailed,
                modifier = Modifier.fillMaxSize().padding(padding),
                retryLabel = retryLabel,
                onRetry = { scope.launch { loadUpdates() } }
            )
            else -> {
                val myDisplayName = currentUserId?.let { displayNameByUserId[it] } ?: myStatus
                val myAvatarUrl = currentUserId?.let { avatarByUserId[it] }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    item(key = "my_status") {
                        val myStatusShape = RoundedCornerShape(MuhabbetCorners.Bubble)
                        Surface(
                            shape = myStatusShape,
                            color = MuhabbetDepth.Raised.containerColor(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small)
                                .pressable(shape = myStatusShape) { showStatusInput = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MuhabbetSpacing.Large),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    UserAvatar(
                                        avatarUrl = myAvatarUrl,
                                        displayName = myDisplayName,
                                        size = MuhabbetSizes.AvatarMedium
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(20.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Muhabbet.icons.Add,
                                                contentDescription = statusAdd,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.width(MuhabbetSpacing.Medium))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = myStatus,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = statusAdd,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (statusGroups.isEmpty()) {
                        item(key = "updates_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.XXLarge),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = noStatuses,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        item(key = "updates_header") {
                            SectionHeader(
                                title = updatesRecent,
                                dotColor = MaterialTheme.colorScheme.primary
                            )
                        }

                        items(statusGroups, key = { it.userId }) { group ->
                            // A locally-known name wins (it may be the address-book name), then the
                            // name the server resolved, and finally a plain label. Never the user
                            // id: its first eight characters read as a hex hash, which is what #507
                            // reported as a leaked phone hash.
                            val displayName = displayNameByUserId[group.userId]
                                ?: group.displayName
                                ?: unknownPersonLabel
                            val avatarUrl = avatarByUserId[group.userId] ?: group.avatarUrl
                            val latestStatusTime = group.statuses.maxOfOrNull { it.createdAt } ?: 0L
                            val meta = pluralStringResource(
                                Res.plurals.updates_status_meta,
                                group.statuses.size,
                                group.statuses.size,
                                DateTimeFormatter.formatTime(latestStatusTime)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStatusClick(group.userId, displayName) }
                                    .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .size(MuhabbetSizes.AvatarMedium)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                ) {
                                    UserAvatar(
                                        avatarUrl = avatarUrl,
                                        displayName = displayName,
                                        size = MuhabbetSizes.AvatarMedium
                                    )
                                }

                                Spacer(Modifier.width(MuhabbetSpacing.Medium))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private const val TAG = "UpdatesTabScreen"
