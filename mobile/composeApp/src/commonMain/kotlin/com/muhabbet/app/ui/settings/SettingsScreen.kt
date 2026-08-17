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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.muhabbet.app.data.local.ThemeController
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.data.repository.MediaUploadHelper
import com.muhabbet.app.platform.ImagePickerLauncher
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.app.platform.rememberImagePickerLauncher
import com.muhabbet.app.platform.rememberRestartApp
import com.muhabbet.app.ui.notice.TestBuildNoticeCard
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.StorageUsageResponse
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.SettingsNavRow
import com.muhabbet.designsystem.components.MuhabbetLoadingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    /**
     * Scroll straight to the Language section once, on open.
     *
     * True only for the launch that follows a language change: applying a locale on Android means
     * recreating the Activity, which used to return the user to the top of the conversation list
     * from wherever they were in Settings (#505). They now come back to the control they just used,
     * which is also where the change is visible.
     */
    focusLanguageSection: Boolean = false,
    onStarredMessages: () -> Unit = {},
    onPrivacyDashboard: () -> Unit = {},
    onTwoStepVerification: () -> Unit = {},
    onAppLock: () -> Unit = {},
    onWallpaper: () -> Unit = {},
    onAbout: () -> Unit = {},
    authRepository: AuthRepository = koinInject(),
    mediaRepository: MediaRepository = koinInject(),
    mediaUploadHelper: MediaUploadHelper = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var displayName by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    // The Account section used to print getUserId() under the label "Telefon", so every user saw a
    // UUID where their phone number should be. GET /users/me already returns the number — and this
    // screen already called it — the value was simply dropped on the floor.
    var phoneNumber by remember { mutableStateOf("") }
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

    // Scroll plumbing for [focusLanguageSection].
    //
    // Both positions are read in WINDOW coordinates, so the sum below needs no knowledge of the
    // heights above the Language section — only of how far below the top of the viewport it
    // currently sits. The screen always opens at scroll 0, so that distance is the scroll offset.
    val scrollState = rememberScrollState()
    var viewportTop by remember { mutableStateOf<Float?>(null) }
    var languageSectionTop by remember { mutableStateOf<Float?>(null) }

    // Keyed on Unit and awaiting the measurements through a snapshot flow, rather than keying the
    // effect on the measurements themselves: scrolling MOVES the Language section, so a
    // measurement-keyed effect would cancel and relaunch itself in the middle of its own scroll.
    LaunchedEffect(Unit) {
        if (!focusLanguageSection) return@LaunchedEffect
        val (top, language) = snapshotFlow {
            val viewport = viewportTop
            val section = languageSectionTop
            if (viewport != null && section != null) viewport to section else null
        }.filterNotNull().first()
        // Instant, not animated. The user has just come back from a full process restart; arriving
        // already at the control they used reads as the app keeping their place, whereas the page
        // scrolling itself on the first frame reads as the app doing something else again.
        scrollState.scrollTo((scrollState.value + (language - top)).roundToInt().coerceAtLeast(0))
    }

    val profileUpdatedMsg = stringResource(Res.string.settings_profile_updated)
    val genericErrorMsg = stringResource(Res.string.error_generic)
    val photoUploadFailedMsg = stringResource(Res.string.profile_photo_failed)
    val privacySaveFailedMsg = stringResource(Res.string.privacy_settings_save_failed)

    val imagePickerLauncher: ImagePickerLauncher = rememberImagePickerLauncher { picked: PickedImage? ->
        if (picked == null) return@rememberImagePickerLauncher
        scope.launch {
            isUploadingPhoto = true
            var uploadFailed = false
            try {
                val uploadResponse = mediaUploadHelper.uploadProfilePhoto(
                    bytes = picked.bytes,
                    fileName = picked.fileName
                )
                authRepository.updateProfile(avatarUrl = uploadResponse.url)
                avatarUrl = uploadResponse.url
            } catch (e: Exception) {
                Log.e(TAG, "Profile photo upload failed", e)
                uploadFailed = true
            }
            // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
            // Both outcomes reported here, because the success snackbar held the avatar spinner
            // just as long as the failure one did.
            isUploadingPhoto = false
            snackbarHostState.showSnackbar(
                if (uploadFailed) photoUploadFailedMsg else profileUpdatedMsg
            )
        }
    }

    LaunchedEffect(Unit) {
        val failure = runCatchingCancellable {
            val profile = authRepository.getProfile()
            displayName = profile.displayName ?: ""
            about = profile.about ?: ""
            avatarUrl = profile.avatarUrl
            phoneNumber = profile.phoneNumber ?: ""
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        if (failure != null) {
            // The form stays blank on failure, and Save writes what the form holds — so a load the
            // user was never told about is how a display name gets overwritten with "".
            Log.e(TAG, "Failed to load profile", failure)
            snackbarHostState.showSnackbar(genericErrorMsg)
        }
    }

    LaunchedEffect(Unit) {
        // Deliberately absorbed. This is the storage-usage card only; the profile above already
        // reports an outage, and the card simply does not render its numbers.
        runCatchingCancellable { storageUsage = mediaRepository.getStorageUsage() }
            .onFailure { e -> Log.e(TAG, "Failed to load storage usage", e) }
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
            MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // Before verticalScroll on purpose — see the scroll plumbing above.
                    .onGloballyPositioned { viewportTop = it.positionInWindow().y }
                    .verticalScroll(scrollState)
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
                            var saveFailed = false
                            try {
                                authRepository.updateProfile(
                                    displayName = displayName.ifBlank { null },
                                    about = about.ifBlank { null }
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to save profile", e)
                                saveFailed = true
                            }
                            // Clear the spinner BEFORE reporting — showSnackbar suspends until
                            // dismissed (~4s). Both outcomes, for the same reason as the photo
                            // upload above: a successful save spun the button just as long.
                            isSaving = false
                            snackbarHostState.showSnackbar(
                                if (saveFailed) genericErrorMsg else profileUpdatedMsg
                            )
                        }
                    }
                )

                Spacer(Modifier.height(MuhabbetSpacing.XXLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                // App section
                SettingsSectionTitle(stringResource(Res.string.settings_app_section))
                Spacer(Modifier.height(MuhabbetSpacing.Medium))

                // Carries the version, and says in one breath what that version is (#519). This
                // replaced a bare "Sürüm: 0.3.4" line: the number and the caveat are one fact, and
                // a screen that printed the version twice would read as an oversight.
                TestBuildNoticeCard()

                Spacer(Modifier.height(MuhabbetSpacing.Small))

                // The OFL is satisfied by bundling the licence (mobile/designsystem/licenses/), but
                // naming the typeface is the decent thing to do and costs one line.
                Text(
                    text = stringResource(Res.string.settings_font_attribution),
                    style = MaterialTheme.typography.bodySmall,
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
                Spacer(Modifier.height(MuhabbetSpacing.Small))

                // The only route into AboutScreen — build info and the three legal documents that,
                // before #614, existed on the website but were unreachable from inside the app.
                SettingsNavRow(
                    title = stringResource(Res.string.about_title),
                    icon = Muhabbet.icons.Info,
                    iconContentDescription = stringResource(Res.string.about_title),
                    onClick = onAbout
                )

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                StorageSection(storageLoading = storageLoading, storageUsage = storageUsage)

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))

                // A Column, not a Box: LanguageSection emits a title, a spacer and two rows, and a
                // Box would stack them on top of each other.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { languageSectionTop = it.positionInWindow().y }
                ) {
                    LanguageSection(tokenStorage = tokenStorage, restartApp = restartApp)
                }

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

                PrivacySection(
                    onSaveFailed = {
                        scope.launch { snackbarHostState.showSnackbar(privacySaveFailedMsg) }
                    }
                )

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                NotificationsSection()

                Spacer(Modifier.height(MuhabbetSpacing.XLarge))
                HorizontalDivider()
                Spacer(Modifier.height(MuhabbetSpacing.Large))

                AccountSection(phoneNumber = phoneNumber)

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

private const val TAG = "SettingsScreen"
