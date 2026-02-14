package com.muhabbet.app.ui.settings

import com.muhabbet.app.ui.components.ConfirmDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.RadioButton
import coil3.compose.AsyncImage
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.platform.ImagePickerLauncher
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.app.platform.compressImage
import com.muhabbet.app.platform.rememberImagePickerLauncher
import com.muhabbet.app.platform.rememberRestartApp
import com.muhabbet.app.ui.components.UserAvatar
import com.muhabbet.app.ui.theme.MuhabbetElevation
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import androidx.compose.ui.layout.ContentScale
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.StorageUsageResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onStarredMessages: () -> Unit = {},
    authRepository: AuthRepository = koinInject(),
    mediaRepository: MediaRepository = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var displayName by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(tokenStorage.getLanguage() ?: "tr") }
    var storageUsage by remember { mutableStateOf<StorageUsageResponse?>(null) }
    var storageLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val restartApp = rememberRestartApp()

    val profileUpdatedMsg = stringResource(Res.string.settings_profile_updated)
    val genericErrorMsg = stringResource(Res.string.error_generic)
    val photoUploadFailedMsg = stringResource(Res.string.profile_photo_failed)

    val imagePickerLauncher: ImagePickerLauncher = rememberImagePickerLauncher { picked: PickedImage? ->
        if (picked == null) return@rememberImagePickerLauncher
        scope.launch {
            isUploadingPhoto = true
            try {
                val compressed = compressImage(picked.bytes)
                val uploadResponse = mediaRepository.uploadImage(
                    bytes = compressed,
                    mimeType = "image/jpeg",
                    fileName = picked.fileName
                )
                authRepository.updateProfile(avatarUrl = uploadResponse.url)
                avatarUrl = uploadResponse.url
                snackbarHostState.showSnackbar(profileUpdatedMsg)
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(photoUploadFailedMsg)
            }
            isUploadingPhoto = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            val profile = authRepository.getProfile()
            displayName = profile.displayName ?: ""
            about = profile.about ?: ""
            avatarUrl = profile.avatarUrl
        } catch (_: Exception) { }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        try {
            storageUsage = mediaRepository.getStorageUsage()
        } catch (_: Exception) { }
        storageLoading = false
    }

    if (showLogoutDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.logout_confirm_title),
            message = stringResource(Res.string.logout_confirm_message),
            confirmLabel = stringResource(Res.string.logout_confirm_yes),
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false },
            isDestructive = true,
            dismissLabel = stringResource(Res.string.logout_confirm_no)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.action_back)
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
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(MuhabbetSpacing.XXLarge))
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(MuhabbetSpacing.XLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar with camera overlay
                Box(contentAlignment = Alignment.Center) {
                    UserAvatar(
                        avatarUrl = avatarUrl,
                        displayName = displayName,
                        size = 80.dp,
                        modifier = Modifier.clickable(enabled = !isUploadingPhoto) {
                            imagePickerLauncher.launch()
                        }
                    )
                    // Camera icon overlay
                    Surface(
                        modifier = Modifier.size(28.dp).align(Alignment.BottomEnd),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isUploadingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = stringResource(Res.string.profile_change_photo),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))

                // Profile section
                Text(
                    text = stringResource(Res.string.settings_profile_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 64) displayName = it },
                    label = { Text(stringResource(Res.string.settings_display_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                OutlinedTextField(
                    value = about,
                    onValueChange = { if (it.length <= 140) about = it },
                    label = { Text(stringResource(Res.string.settings_about)) },
                    placeholder = { Text(stringResource(Res.string.settings_about_placeholder)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(MuhabbetSpacing.Large))

                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                authRepository.updateProfile(
                                    displayName = displayName.ifBlank { null },
                                    about = about.ifBlank { null }
                                )
                                snackbarHostState.showSnackbar(profileUpdatedMsg)
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar(genericErrorMsg)
                            }
                            isSaving = false
                        }
                    },
                    enabled = !isSaving && displayName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(stringResource(Res.string.settings_save))
                    }
                }

                Spacer(Modifier.height(MuhabbetSpacing.XXLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                // App section
                Text(
                    text = stringResource(Res.string.settings_app_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                Text(
                    text = "${stringResource(Res.string.settings_version)}: ${com.muhabbet.app.BuildInfo.VERSION}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                Surface(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onStarredMessages() },
                    tonalElevation = MuhabbetElevation.Level1,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = MuhabbetSpacing.Medium, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = stringResource(Res.string.starred_title),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(Res.string.starred_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                // Storage usage section
                Text(
                    text = stringResource(Res.string.storage_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                if (storageLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(Res.string.storage_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (storageUsage != null) {
                    val usage = storageUsage ?: return@item
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = MuhabbetElevation.Level1,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(MuhabbetSpacing.Medium)) {
                            StorageRow(
                                label = stringResource(Res.string.storage_total),
                                bytes = usage.totalBytes,
                                count = usage.imageCount + usage.audioCount + usage.documentCount,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.Small))
                            StorageRow(
                                label = stringResource(Res.string.storage_images),
                                bytes = usage.imageBytes,
                                count = usage.imageCount,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                            StorageRow(
                                label = stringResource(Res.string.storage_audio),
                                bytes = usage.audioBytes,
                                count = usage.audioCount,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                            StorageRow(
                                label = stringResource(Res.string.storage_documents),
                                bytes = usage.documentBytes,
                                count = usage.documentCount,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.storage_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))

                // Language section
                Text(
                    text = stringResource(Res.string.settings_language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            selectedLanguage = "tr"
                            tokenStorage.setLanguage("tr")
                            restartApp()
                        }
                        .padding(vertical = MuhabbetSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
                ) {
                    RadioButton(
                        selected = selectedLanguage == "tr",
                        onClick = {
                            selectedLanguage = "tr"
                            tokenStorage.setLanguage("tr")
                            restartApp()
                        }
                    )
                    Text(
                        text = stringResource(Res.string.settings_language_turkish),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            selectedLanguage = "en"
                            tokenStorage.setLanguage("en")
                            restartApp()
                        }
                        .padding(vertical = MuhabbetSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
                ) {
                    RadioButton(
                        selected = selectedLanguage == "en",
                        onClick = {
                            selectedLanguage = "en"
                            tokenStorage.setLanguage("en")
                            restartApp()
                        }
                    )
                    Text(
                        text = stringResource(Res.string.settings_language_english),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                // Theme section
                Text(
                    text = stringResource(Res.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))

                var selectedTheme by remember { mutableStateOf(tokenStorage.getTheme() ?: "system") }
                val themeOptions = listOf(
                    "system" to stringResource(Res.string.settings_theme_system),
                    "light" to stringResource(Res.string.settings_theme_light),
                    "dark" to stringResource(Res.string.settings_theme_dark),
                    "oled" to stringResource(Res.string.settings_theme_oled)
                )
                themeOptions.forEach { (key, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                selectedTheme = key
                                tokenStorage.setTheme(key)
                                restartApp()
                            }
                            .padding(vertical = MuhabbetSpacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
                    ) {
                        RadioButton(
                            selected = selectedTheme == key,
                            onClick = {
                                selectedTheme = key
                                tokenStorage.setTheme(key)
                                restartApp()
                            }
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                // Privacy section
                Text(
                    text = stringResource(Res.string.settings_privacy_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                var readReceiptsEnabled by remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = MuhabbetSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.settings_privacy_read_receipts),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(Res.string.settings_privacy_read_receipts_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = readReceiptsEnabled,
                        onCheckedChange = { readReceiptsEnabled = it }
                    )
                }

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                // Notifications section
                Text(
                    text = stringResource(Res.string.settings_notifications_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                var notificationsEnabled by remember { mutableStateOf(true) }
                var vibrationEnabled by remember { mutableStateOf(true) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = MuhabbetSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.settings_notifications_enabled),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    androidx.compose.material3.Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = MuhabbetSpacing.Small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(Res.string.settings_notifications_vibrate),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    androidx.compose.material3.Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = { vibrationEnabled = it }
                    )
                }

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                // Account section
                Text(
                    text = stringResource(Res.string.settings_account_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                val phoneNumber = remember { tokenStorage.getUserId() ?: "" }
                Text(
                    text = "${stringResource(Res.string.settings_account_phone)}: $phoneNumber",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = stringResource(Res.string.settings_logout),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(MuhabbetSpacing.Small))
                    Text(stringResource(Res.string.settings_logout))
                }
            }
        }
    }
}

@Composable
private fun StorageRow(label: String, bytes: Long, count: Int, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = "${formatBytes(bytes)} ($count)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}
