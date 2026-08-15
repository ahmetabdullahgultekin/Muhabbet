package com.muhabbet.app.ui.settings

import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.ConfirmDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
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
import com.muhabbet.app.data.local.ThemeController
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.data.repository.MediaUploadHelper
import com.muhabbet.app.platform.ImagePickerLauncher
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.app.platform.rememberImagePickerLauncher
import com.muhabbet.app.platform.rememberRestartApp
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.util.Log
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.StorageUsageResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.SettingsNavRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onStarredMessages: () -> Unit = {},
    onPrivacyDashboard: () -> Unit = {},
    onTwoStepVerification: () -> Unit = {},
    onAppLock: () -> Unit = {},
    onWallpaper: () -> Unit = {},
    authRepository: AuthRepository = koinInject(),
    mediaRepository: MediaRepository = koinInject(),
    mediaUploadHelper: MediaUploadHelper = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var displayName by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var storageUsage by remember { mutableStateOf<StorageUsageResponse?>(null) }
    var storageLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val restartApp = rememberRestartApp()
    val themeController: ThemeController = koinInject()

    val profileUpdatedMsg = stringResource(Res.string.settings_profile_updated)
    val genericErrorMsg = stringResource(Res.string.error_generic)
    val photoUploadFailedMsg = stringResource(Res.string.profile_photo_failed)

    val imagePickerLauncher: ImagePickerLauncher = rememberImagePickerLauncher { picked: PickedImage? ->
        if (picked == null) return@rememberImagePickerLauncher
        scope.launch {
            isUploadingPhoto = true
            try {
                val uploadResponse = mediaUploadHelper.uploadProfilePhoto(
                    bytes = picked.bytes,
                    fileName = picked.fileName
                )
                authRepository.updateProfile(avatarUrl = uploadResponse.url)
                avatarUrl = uploadResponse.url
                snackbarHostState.showSnackbar(profileUpdatedMsg)
            } catch (e: Exception) {
                Log.e("SettingsScreen", "Profile photo upload failed", e)
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
        } catch (e: Exception) {
            Log.e("SettingsScreen", "Failed to load profile", e)
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        try {
            storageUsage = mediaRepository.getStorageUsage()
        } catch (e: Exception) {
            Log.e("SettingsScreen", "Failed to load storage usage", e)
        }
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

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.settings_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        snackbarHostState = snackbarHostState
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
                ProfileEditorSection(
                    avatarUrl = avatarUrl,
                    displayName = displayName,
                    about = about,
                    isUploadingPhoto = isUploadingPhoto,
                    isSaving = isSaving,
                    onPickPhoto = { imagePickerLauncher.launch() },
                    onDisplayNameChange = { displayName = it },
                    onAboutChange = { about = it },
                    onSave = {
                        isSaving = true
                        scope.launch {
                            try {
                                authRepository.updateProfile(
                                    displayName = displayName.ifBlank { null },
                                    about = about.ifBlank { null }
                                )
                                snackbarHostState.showSnackbar(profileUpdatedMsg)
                            } catch (e: Exception) {
                                Log.e("SettingsScreen", "Failed to save profile", e)
                                snackbarHostState.showSnackbar(genericErrorMsg)
                            }
                            isSaving = false
                        }
                    }
                )

                Spacer(Modifier.height(MuhabbetSpacing.XXLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                // App section
                SettingsSectionTitle(stringResource(Res.string.settings_app_section))
                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                Text(
                    text = "${stringResource(Res.string.settings_version)}: ${com.muhabbet.app.BuildInfo.VERSION}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                SettingsNavRow(
                    title = stringResource(Res.string.starred_title),
                    icon = Muhabbet.icons.Star,
                    iconContentDescription = stringResource(Res.string.starred_title),
                    onClick = onStarredMessages
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                SettingsNavRow(
                    title = stringResource(Res.string.two_step_title),
                    icon = Muhabbet.icons.TwoStep,
                    iconContentDescription = stringResource(Res.string.two_step_title),
                    onClick = onTwoStepVerification
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                SettingsNavRow(
                    title = stringResource(Res.string.app_lock_title),
                    icon = Muhabbet.icons.Lock,
                    iconContentDescription = stringResource(Res.string.app_lock_title),
                    onClick = onAppLock
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                SettingsNavRow(
                    title = stringResource(Res.string.wallpaper_title),
                    icon = Muhabbet.icons.Wallpaper,
                    iconContentDescription = stringResource(Res.string.wallpaper_title),
                    onClick = onWallpaper
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))

                // Media Quality row
                var showMediaQualityDialog by remember { mutableStateOf(false) }
                SettingsNavRow(
                    title = stringResource(Res.string.media_quality_title),
                    icon = Muhabbet.icons.MediaQuality,
                    iconContentDescription = stringResource(Res.string.media_quality_title),
                    onClick = { showMediaQualityDialog = true }
                )
                if (showMediaQualityDialog) {
                    MediaQualityDialog(onDismiss = { showMediaQualityDialog = false })
                }

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                StorageSection(storageLoading = storageLoading, storageUsage = storageUsage)

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))

                LanguageSection(tokenStorage = tokenStorage, restartApp = restartApp)

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                ThemeSection(themeController = themeController)

                HorizontalDivider(modifier = Modifier.padding(vertical = MuhabbetSpacing.Large))

                HapticsSection(themeController = themeController)

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                // Privacy Dashboard link
                SettingsNavRow(
                    title = stringResource(Res.string.privacy_open_dashboard),
                    icon = Muhabbet.icons.Privacy,
                    iconContentDescription = stringResource(Res.string.privacy_open_dashboard),
                    onClick = onPrivacyDashboard
                )

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                PrivacySection()

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                NotificationsSection()

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                AccountSection(phoneNumber = remember { tokenStorage.getUserId() ?: "" })

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
                        imageVector = Muhabbet.icons.Logout,
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
