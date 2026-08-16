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
import com.muhabbet.app.data.local.ThemeController
import com.muhabbet.app.data.local.TokenStorage
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
        isUploading = isUploadingPhoto
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
internal fun StorageSection(storageLoading: Boolean, storageUsage: StorageUsageResponse?) {
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
        Surface(
            modifier = Modifier.fillMaxWidth().depth(MuhabbetDepth.Raised, storageCardShape),
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

@Composable
internal fun LanguageSection(tokenStorage: TokenStorage, restartApp: () -> Unit) {
    var selectedLanguage by remember { mutableStateOf(tokenStorage.getLanguage() ?: "tr") }
    SettingsSectionTitle(stringResource(Res.string.settings_language))
    Spacer(Modifier.height(MuhabbetSpacing.Small))

    val options = listOf(
        "tr" to stringResource(Res.string.settings_language_turkish),
        "en" to stringResource(Res.string.settings_language_english)
    )
    options.forEach { (key, label) ->
        SettingsRadioRow(
            title = label,
            selected = selectedLanguage == key,
            onSelect = {
                selectedLanguage = key
                tokenStorage.setLanguage(key)
                restartApp()
            }
        )
    }
}

/**
 * Theme picker. Unlike [LanguageSection] this does not restart the app — [ThemeController] is read
 * at the composition root, so a new mode repaints the tree in place.
 */
@Composable
internal fun ThemeSection(themeController: ThemeController) {
    val selectedTheme by themeController.mode.collectAsState()
    SettingsSectionTitle(stringResource(Res.string.settings_theme))
    Spacer(Modifier.height(MuhabbetSpacing.Small))

    val themeOptions = listOf(
        MuhabbetThemeMode.System to stringResource(Res.string.settings_theme_system),
        MuhabbetThemeMode.Light to stringResource(Res.string.settings_theme_light),
        MuhabbetThemeMode.Dark to stringResource(Res.string.settings_theme_dark),
        MuhabbetThemeMode.Oled to stringResource(Res.string.settings_theme_oled)
    )
    themeOptions.forEach { (mode, label) ->
        SettingsRadioRow(
            title = label,
            selected = selectedTheme == mode,
            onSelect = { themeController.set(mode) }
        )
    }
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
 * Notifications, honestly.
 *
 * This section used to hold "Bildirimler açık" and "Titreşim" as local state. Neither had anywhere
 * to go: push delivery is off in the deploying stack (`FCM_ENABLED: "false"` in the repo-root
 * `docker-compose.prod.yml`), so the server never sends a push and no preference could change that.
 *
 * The switches are gone rather than persisted. Storing a flag would have meant new storage members
 * on three implementations for a value with no consumer — the same shape as the defect being fixed
 * elsewhere in this change, and it would have read to the user as "notifications are on" while the
 * phone stayed silent. A statement of what the app actually does is more use than a switch that
 * does nothing. Restore the controls together with push, not before it.
 */
@Composable
internal fun NotificationsSection() {
    SettingsSectionTitle(stringResource(Res.string.settings_notifications_section))
    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    SettingsInfoRow(
        title = stringResource(Res.string.settings_notifications_unavailable),
        subtitle = stringResource(Res.string.settings_notifications_unavailable_desc),
        icon = Muhabbet.icons.Info
    )
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

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${formatDecimal(kb, 1)} KB"
    val mb = kb / 1024.0
    if (mb < 1024) return "${formatDecimal(mb, 1)} MB"
    val gb = mb / 1024.0
    return "${formatDecimal(gb, 2)} GB"
}

private fun formatDecimal(value: Double, places: Int): String {
    var factor = 1L
    repeat(places) { factor *= 10 }
    val rounded = ((value * factor) + 0.5).toLong()
    val intPart = rounded / factor
    val fracPart = (rounded % factor).toString().padStart(places, '0')
    return "$intPart.$fracPart"
}
