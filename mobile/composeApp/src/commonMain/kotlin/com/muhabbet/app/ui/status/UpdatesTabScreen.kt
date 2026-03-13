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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.muhabbet.app.ui.components.SectionHeader
import com.muhabbet.app.ui.components.UserAvatar
import com.muhabbet.app.ui.theme.MuhabbetElevation
import com.muhabbet.app.ui.theme.MuhabbetSizes
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
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
import com.muhabbet.composeapp.generated.resources.updates_recent
import com.muhabbet.composeapp.generated.resources.updates_status_meta
import com.muhabbet.composeapp.generated.resources.updates_title
import com.muhabbet.shared.dto.UserStatusGroup
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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
    val settingsTitle = stringResource(Res.string.settings_title)

    suspend fun loadUpdates() {
        isLoading = true
        errorMessage = null
        try {
            val groups = statusRepository.getContactStatuses()
                .filter { it.statuses.isNotEmpty() }
                .sortedByDescending { group ->
                    group.statuses.maxOfOrNull { it.createdAt } ?: 0L
                }
            statusGroups = groups

            val participants = conversationRepository.getConversations().items
                .flatMap { it.participants }
                .associateBy { it.userId }

            displayNameByUserId = participants.mapValues { (_, participant) ->
                participant.displayName ?: participant.phoneNumber ?: participant.userId.take(8)
            }
            avatarByUserId = participants.mapValues { (_, participant) -> participant.avatarUrl }
        } catch (_: Exception) {
            errorMessage = loadFailed
        }
        isLoading = false
    }

    LaunchedEffect(refreshKey) {
        loadUpdates()
    }

    if (showStatusInput) {
        AlertDialog(
            onDismissRequest = {
                if (!isUploadingStatus) {
                    showStatusInput = false
                    statusText = ""
                    statusPickedImage = null
                }
            },
            title = { Text(statusCreateTitle) },
            text = {
                Column {
                    OutlinedTextField(
                        value = statusText,
                        onValueChange = { statusText = it },
                        placeholder = { Text(statusPlaceholder) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { statusImagePicker.launch() },
                            enabled = !isUploadingStatus
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
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
                                .size(24.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = statusText.trim()
                        if (text.isEmpty() && statusPickedImage == null) return@TextButton
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
                                showStatusInput = false
                                statusText = ""
                                statusPickedImage = null
                            } catch (_: Exception) {
                                errorMessage = loadFailed
                            }
                            isUploadingStatus = false
                        }
                    },
                    enabled = (statusText.isNotBlank() || statusPickedImage != null) && !isUploadingStatus
                ) {
                    Text(statusPost)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showStatusInput = false
                        statusText = ""
                        statusPickedImage = null
                    },
                    enabled = !isUploadingStatus
                ) {
                    Text(cancelText)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(updatesTitle, fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = settingsTitle
                            )
                        }
                    }
                )
            }
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: loadFailed,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> {
                val myDisplayName = currentUserId?.let { displayNameByUserId[it] } ?: myStatus
                val myAvatarUrl = currentUserId?.let { avatarByUserId[it] }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    item(key = "my_status") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            tonalElevation = MuhabbetElevation.Level1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small)
                                .clickable { showStatusInput = true }
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
                                                imageVector = Icons.Default.Add,
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
                            val displayName = displayNameByUserId[group.userId] ?: group.userId.take(8)
                            val avatarUrl = avatarByUserId[group.userId]
                            val latestStatusTime = group.statuses.maxOfOrNull { it.createdAt } ?: 0L
                            val meta = stringResource(
                                Res.string.updates_status_meta,
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
