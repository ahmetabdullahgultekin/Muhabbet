package com.muhabbet.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.local.PrivacySettingsController
import com.muhabbet.app.util.formatBytes
import com.muhabbet.app.data.local.ComposerSettingsController
import com.muhabbet.app.data.local.MediaVisibilityController
import com.muhabbet.app.data.local.ThemeController
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.platform.AppVisibility
import com.muhabbet.app.platform.NotificationPermission
import com.muhabbet.app.platform.NotificationPermissionState
import com.muhabbet.app.platform.rememberGallerySavePermissionRequester
import com.muhabbet.app.platform.rememberMediaGallerySaver
import androidx.compose.ui.semantics.Role
import com.muhabbet.designsystem.modifier.pressable
import com.muhabbet.designsystem.components.EditableAvatar
import com.muhabbet.designsystem.theme.MuhabbetThemeMode
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.StorageUsageResponse
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.SettingsInfoRow
import com.muhabbet.designsystem.components.SettingsNavRow
import com.muhabbet.designsystem.components.SettingsSwitchRow
import com.muhabbet.designsystem.components.SettingsRadioRow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.theme.containerColor
import com.muhabbet.designsystem.theme.depth
import com.muhabbet.designsystem.theme.MuhabbetDepth

/**
 * Avatar (with camera overlay) + display-name / about fields + save button.
 * State is hoisted into [SettingsScreen]; this composable is purely presentational.
 */
@Composable
internal fun ProfileEditorSection(
    avatarUrl: String?,
    displayName: String,
    about: String,
    isUploadingPhoto: Boolean,
    isSaving: Boolean,
    onPickPhoto: () -> Unit,
    onViewPhoto: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onAboutChange: (String) -> Unit,
    onSave: () -> Unit
) {
    EditableAvatar(
        avatarUrl = avatarUrl,
        displayName = displayName,
        size = MuhabbetSizes.AvatarXXLarge,
        changePhotoContentDescription = stringResource(Res.string.profile_change_photo),
        onPickPhoto = onPickPhoto,
        isUploading = isUploadingPhoto,
        onViewPhoto = onViewPhoto
    )

    Spacer(Modifier.height(MuhabbetSpacing.XLarge))

    SettingsSectionTitle(stringResource(Res.string.settings_profile_section))
    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    MuhabbetTextField(
        value = displayName,
        onValueChange = { if (it.length <= 64) onDisplayNameChange(it) },
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(Res.string.settings_display_name),
        singleLine = true
    )

    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    MuhabbetTextField(
        value = about,
        onValueChange = { if (it.length <= 140) onAboutChange(it) },
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(Res.string.settings_about),
        placeholder = stringResource(Res.string.settings_about_placeholder),
        singleLine = false,
        maxLines = 3
    )

    Spacer(Modifier.height(MuhabbetSpacing.Large))

    Button(
        onClick = onSave,
        enabled = !isSaving && displayName.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(MuhabbetSizes.IconMedium),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(stringResource(Res.string.settings_save))
        }
    }
}

@Composable
internal fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun StorageSection(
    storageLoading: Boolean,
    storageUsage: StorageUsageResponse?,
    /**
     * Where tapping the card goes, or null when it is already the destination.
     *
     * Null on `StorageUsageScreen`, which reuses this card to show the same four numbers — a card
     * that navigates to the screen it is on would be a dead tap, and the ripple would say
     * otherwise. The whole surface is the target rather than a separate "manage" row: the numbers
     * are the thing being asked about, so they are the thing to press (#546).
     */
    onOpenDetail: (() -> Unit)? = null
) {
    SettingsSectionTitle(stringResource(Res.string.storage_title))
    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    if (storageLoading) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(MuhabbetSizes.IconSmall), strokeWidth = 2.dp)
            Text(
                text = stringResource(Res.string.storage_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else if (storageUsage != null) {
        val storageCardShape = MaterialTheme.shapes.small
        val openLabel = stringResource(Res.string.storage_open_detail)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .depth(MuhabbetDepth.Raised, storageCardShape)
                // Through `pressable` rather than a bare `clickable`, so the ripple follows the
                // card corners instead of flashing a rectangle over them (#703). The label is what
                // a screen reader announces for the action, which a card of four numbers otherwise
                // gives no clue about.
                .then(
                    if (onOpenDetail != null) {
                        Modifier.pressable(
                            shape = storageCardShape,
                            onClickLabel = openLabel,
                            role = Role.Button,
                            onClick = onOpenDetail
                        )
                    } else {
                        Modifier
                    }
                ),
            color = MuhabbetDepth.Raised.containerColor(),
            shape = storageCardShape
        ) {
            Column(modifier = Modifier.padding(MuhabbetSpacing.Medium)) {
                StorageRow(
                    label = stringResource(Res.string.storage_total),
                    bytes = storageUsage.totalBytes,
                    count = storageUsage.imageCount + storageUsage.audioCount + storageUsage.documentCount,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                StorageRow(
                    label = stringResource(Res.string.storage_images),
                    bytes = storageUsage.imageBytes,
                    count = storageUsage.imageCount,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                StorageRow(
                    label = stringResource(Res.string.storage_audio),
                    bytes = storageUsage.audioBytes,
                    count = storageUsage.audioCount,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                StorageRow(
                    label = stringResource(Res.string.storage_documents),
                    bytes = storageUsage.documentBytes,
                    count = storageUsage.documentCount,
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
}

/**
 * Language picker.
 *
 * The filled row is the language actually rendering — see [selectedLanguage] — never a
 * default-locale constant and never the stored preference, which is a different question and can
 * disagree (#548). Selecting a language persists it, and when that language is not the one on
 * screen it marks the restart so the replacement process comes back here rather than to the
 * conversation list, then restarts: on Android the locale is applied in `MainActivity.onCreate`, so
 * nothing below that point can apply it in place.
 *
 * The tap is no longer skipped for the row that is already filled in. Persisting is idempotent, and
 * it is the one case that matters: a user whose preference says Turkish while the app renders
 * English sees English filled in, and tapping Türkçe now restarts into Turkish instead of being
 * swallowed as "already selected".
 */
@Composable
internal fun LanguageSection(tokenStorage: TokenStorage, restartApp: () -> Unit) {
    // Read once: the stored value cannot change while this screen is open except through the tap
    // below, and that either ends in a restart or writes back the value already there.
    val stored = remember { tokenStorage.getLanguage() }
    // Deliberately NOT remembered and deliberately a resource lookup: this asks the resource system
    // which locale it resolved for this very composition, so the filled row cannot disagree with the
    // language of the text beside it.
    val rendered = stringResource(Res.string.app_language_code)
    val selected = selectedLanguage(stored = stored, rendered = rendered)

    SettingsSectionTitle(stringResource(Res.string.settings_language))
    Spacer(Modifier.height(MuhabbetSpacing.Small))

    AppLanguage.entries.forEach { language ->
        SettingsRadioRow(
            title = languageLabel(language),
            selected = language == selected,
            onSelect = {
                // Written every time, including for the row already filled in: on a device whose
                // locale the app happens to match there is no stored preference at all, and pinning
                // it is what keeps the app in this language if the device's ever changes.
                tokenStorage.setLanguage(language.code)
                if (languageNeedsRestart(language, rendered)) {
                    tokenStorage.setPendingLanguageRestart()
                    restartApp()
                }
            }
        )
    }
}

/** Exhaustive on purpose: a new [AppLanguage] must fail to compile until it has been given a label. */
@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.Turkish -> stringResource(Res.string.settings_language_turkish)
    AppLanguage.English -> stringResource(Res.string.settings_language_english)
}

/**
 * Theme picker. Unlike [LanguageSection] this does not restart the app — [ThemeController] is read
 * at the composition root, so a new mode repaints the tree in place.
 *
 * The rows come from [MuhabbetThemeMode.entries] rather than a hand-written list. Paired with
 * `fromStorageKey`, which is total, that makes "every row unselected" structurally impossible: the
 * controller's mode is always one of these entries. A hand-written list can fall out of step with
 * the enum, and the way that shows up is a group where nothing is filled in (#505).
 */
@Composable
internal fun ThemeSection(themeController: ThemeController) {
    val selectedTheme by themeController.mode.collectAsState()
    SettingsSectionTitle(stringResource(Res.string.settings_theme))
    Spacer(Modifier.height(MuhabbetSpacing.Small))

    MuhabbetThemeMode.entries.forEach { mode ->
        SettingsRadioRow(
            title = themeLabel(mode),
            selected = selectedTheme == mode,
            onSelect = { themeController.set(mode) }
        )
    }
}

/** Exhaustive on purpose, for the same reason as [languageLabel]. */
@Composable
private fun themeLabel(mode: MuhabbetThemeMode): String = when (mode) {
    MuhabbetThemeMode.System -> stringResource(Res.string.settings_theme_system)
    MuhabbetThemeMode.Light -> stringResource(Res.string.settings_theme_light)
    MuhabbetThemeMode.Dark -> stringResource(Res.string.settings_theme_dark)
    MuhabbetThemeMode.Oled -> stringResource(Res.string.settings_theme_oled)
}

/**
 * The read-receipts switch, backed by the server.
 *
 * It was `remember { mutableStateOf(true) }`: it moved when tapped, told nobody, and reset on the
 * next recomposition. The same control also exists on the privacy dashboard, so the two could show
 * opposite answers — both now read the one [PrivacySettingsController] flow.
 *
 * There is no local default to fall back on. Until the load returns, the row says so rather than
 * showing a guess, because the guess (on) is the setting that leaks the most.
 */
@Composable
internal fun PrivacySection(
    onSaveFailed: () -> Unit,
    privacySettings: PrivacySettingsController = koinInject()
) {
    SettingsSectionTitle(stringResource(Res.string.settings_privacy_section))
    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    val settings by privacySettings.settings.collectAsState()
    var loadFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { loadFailed = privacySettings.load().isFailure }

    val current = settings
    when {
        current != null -> SettingsSwitchRow(
            title = stringResource(Res.string.settings_privacy_read_receipts),
            subtitle = stringResource(Res.string.settings_privacy_read_receipts_subtitle),
            checked = current.readReceiptsEnabled,
            onCheckedChange = { enabled ->
                scope.launch {
                    if (privacySettings.update(readReceiptsEnabled = enabled).isFailure) {
                        onSaveFailed()
                    }
                }
            }
        )

        loadFailed -> SettingsInfoRow(title = stringResource(Res.string.privacy_settings_load_failed))

        else -> SettingsInfoRow(title = stringResource(Res.string.privacy_settings_loading))
    }
}

/**
 * Notifications, honestly — now reporting the one fact that decides whether any of this works: what
 * the phone will do with a notification the app posts.
 *
 * This section used to say push was unavailable, which was true when it was written and is not any
 * more. Push delivery was verified end to end on 2026-08-16; what stopped a real user seeing one was
 * that `POST_NOTIFICATIONS` was declared in the manifest and never requested, so a fresh install on
 * Android 13+ was denied by default (#547).
 *
 * The row is a navigation row, not a switch, in both live states. There is no app-side preference
 * here to store — the switch lives in the OS, and a copy of it in the app would be a second answer
 * that could disagree with the first, which is the defect this file has already been through twice.
 * Tapping goes to the system page, which works whether the user is turning notifications on after a
 * denial or turning them off. It is the only route that still works after two denials, because
 * Android stops showing the permission dialog at that point.
 */
@Composable
internal fun NotificationsSection(
    notificationPermission: NotificationPermission = koinInject(),
    appVisibility: AppVisibility = koinInject()
) {
    SettingsSectionTitle(stringResource(Res.string.settings_notifications_section))
    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    // Re-read on every foreground transition, which is the only way this value can change while the
    // screen is open: the user changes it in the system settings app, off-screen, and comes back.
    // A plain `remember {}` would keep showing "off" after they had just switched it on, and a read
    // on every recomposition would never re-run, because nothing here recomposes on the way back.
    val foreground by appVisibility.isForeground.collectAsState()
    val state = remember(foreground) { notificationPermission.state() }

    when (state) {
        NotificationPermissionState.Enabled -> SettingsNavRow(
            title = stringResource(Res.string.settings_notifications_enabled),
            subtitle = stringResource(Res.string.settings_notifications_enabled_desc),
            // Decorative: the title beside it says the same thing in words, so naming the icon as
            // well would only put a noise word in front of the sentence a screen reader reads out.
            icon = Muhabbet.icons.Notifications,
            onClick = { notificationPermission.openSystemSettings() }
        )

        NotificationPermissionState.Disabled -> SettingsNavRow(
            title = stringResource(Res.string.settings_notifications_disabled),
            subtitle = stringResource(Res.string.settings_notifications_disabled_desc),
            icon = Muhabbet.icons.NotificationsOff,
            onClick = { notificationPermission.openSystemSettings() }
        )

        // iOS. Reports rather than navigates: there is no system page worth opening for a feature
        // the app cannot deliver on this platform at all.
        NotificationPermissionState.Unsupported -> SettingsInfoRow(
            title = stringResource(Res.string.settings_notifications_unavailable),
            subtitle = stringResource(Res.string.settings_notifications_unavailable_desc),
            icon = Muhabbet.icons.Info
        )
    }
}

/**
 * Haptic feedback on or off.
 *
 * Unlike the notification switches above — which are still local state with nowhere to go — this
 * one persists and is read at the composition root, so turning it off silences every haptic in the
 * app through a single check inside [MuhabbetHaptics].
 */
@Composable
internal fun HapticsSection(themeController: ThemeController) {
    val enabled by themeController.hapticsEnabled.collectAsState()
    // No section title: the row's own title is the same string, and the divider above it in
    // SettingsScreen already separates it from the block before.
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_haptics),
        subtitle = stringResource(Res.string.settings_haptics_subtitle),
        checked = enabled,
        onCheckedChange = { themeController.setHapticsEnabled(it) }
    )
}

/**
 * Whether Enter sends (#516).
 *
 * A switch beside haptics rather than a two-row picker: it is a binary with an obvious default, and
 * both halves are named in the subtitle, which is the part that matters — a user who wants the other
 * behaviour needs to be told which key gives it to them, and on a soft keyboard the answer is
 * "turn this off", not "hold Shift".
 *
 * Reads the same [ComposerSettingsController] the composer does. There is deliberately no local
 * `remember` copy here; see that class for what a second copy costs.
 */
@Composable
internal fun EnterToSendSection(composerSettings: ComposerSettingsController = koinInject()) {
    val enabled by composerSettings.enterToSend.collectAsState()
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_enter_to_send),
        subtitle = stringResource(Res.string.settings_enter_to_send_subtitle),
        checked = enabled,
        onCheckedChange = { composerSettings.setEnterToSend(it) }
    )
}

/**
 * Media visibility — whether photos and videos received in chats are copied to the phone's gallery
 * (#593).
 *
 * Three things this row does that a plain switch would not:
 *
 * - **It is absent where it would be a lie.** A platform that cannot write to a shared gallery (iOS
 *   without `NSPhotoLibraryAddUsageDescription` in the host app) gets no row at all, rather than a
 *   switch that saves a preference nothing can act on.
 * - **It asks for the permission when you turn it on**, not when the first photo happens to arrive.
 *   Below Android API 29 this needs storage access; without it the auto-saver would return
 *   PERMISSION_REQUIRED for every message and the switch would sit on, doing nothing.
 * - **It refuses to turn on if the permission is denied**, so the stored value and what the app can
 *   actually do never disagree. Turning it *off* is unconditional — revoking a preference must
 *   never be blocked on a dialog.
 *
 * Reads the same [MediaVisibilityController] the auto-saver does; see that class for what a second
 * copy of this value would cost.
 */
@Composable
internal fun MediaVisibilitySection(
    mediaVisibility: MediaVisibilityController = koinInject()
) {
    val gallerySaver = rememberMediaGallerySaver()
    if (!gallerySaver.isSupported()) return

    val enabled by mediaVisibility.saveToGallery.collectAsState()
    val requestPermission = rememberGallerySavePermissionRequester { granted ->
        if (granted) mediaVisibility.setSaveToGallery(true)
    }
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_media_visibility),
        subtitle = stringResource(Res.string.settings_media_visibility_subtitle),
        checked = enabled,
        onCheckedChange = { wantsOn ->
            if (wantsOn) requestPermission() else mediaVisibility.setSaveToGallery(false)
        }
    )
}

@Composable
internal fun AccountSection(phoneNumber: String) {
    SettingsSectionTitle(stringResource(Res.string.settings_account_section))
    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    // Blank means the profile request failed — say so, rather than printing "Telefon numarası: "
    // with nothing after it. The caller no longer passes a user id here (it used to, so this line
    // rendered a UUID under a phone-number label).
    val value = phoneNumber.ifBlank { stringResource(Res.string.settings_account_phone_unknown) }
    Text(
        text = "${stringResource(Res.string.settings_account_phone)}: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun StorageRow(label: String, bytes: Long, count: Int, color: Color) {
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
