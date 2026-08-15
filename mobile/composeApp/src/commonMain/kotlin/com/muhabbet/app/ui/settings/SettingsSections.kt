package com.muhabbet.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.local.ThemeController
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetThemeMode
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.StorageUsageResponse
import org.jetbrains.compose.resources.stringResource
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.SettingsSwitchRow
import com.muhabbet.designsystem.components.SettingsRadioRow

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
    // The click sits on the whole Box, not just the avatar. The camera badge is drawn over the
    // avatar's bottom-right corner, so a tap that landed on the badge — the part that looks like
    // the button, and the node that carries the "Change photo" description a screen reader is told
    // to activate — hit the Surface and did nothing at all.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.clickable(enabled = !isUploadingPhoto) { onPickPhoto() }
    ) {
        UserAvatar(
            avatarUrl = avatarUrl,
            displayName = displayName,
            size = MuhabbetSizes.AvatarXXLarge
        )
        Surface(
            modifier = Modifier.size(28.dp).align(Alignment.BottomEnd),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isUploadingPhoto) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(MuhabbetSizes.IconSmall),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Muhabbet.icons.Camera,
                        contentDescription = stringResource(Res.string.profile_change_photo),
                        modifier = Modifier.size(MuhabbetSizes.IconSmall),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(MuhabbetSpacing.XLarge))

    SettingsSectionTitle(stringResource(Res.string.settings_profile_section))
    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    OutlinedTextField(
        value = displayName,
        onValueChange = { if (it.length <= 64) onDisplayNameChange(it) },
        label = { Text(stringResource(Res.string.settings_display_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    OutlinedTextField(
        value = about,
        onValueChange = { if (it.length <= 140) onAboutChange(it) },
        label = { Text(stringResource(Res.string.settings_about)) },
        placeholder = { Text(stringResource(Res.string.settings_about_placeholder)) },
        maxLines = 3,
        modifier = Modifier.fillMaxWidth()
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = MuhabbetElevation.Level1,
            shape = MaterialTheme.shapes.small
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

@Composable
internal fun PrivacySection() {
    SettingsSectionTitle(stringResource(Res.string.settings_privacy_section))
    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    var readReceiptsEnabled by remember { mutableStateOf(true) }
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_privacy_read_receipts),
        subtitle = stringResource(Res.string.settings_privacy_read_receipts_subtitle),
        checked = readReceiptsEnabled,
        onCheckedChange = { readReceiptsEnabled = it }
    )
}

@Composable
internal fun NotificationsSection() {
    SettingsSectionTitle(stringResource(Res.string.settings_notifications_section))
    Spacer(Modifier.height(MuhabbetSpacing.Medium))

    var notificationsEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_notifications_enabled),
        checked = notificationsEnabled,
        onCheckedChange = { notificationsEnabled = it }
    )
    SettingsSwitchRow(
        title = stringResource(Res.string.settings_notifications_vibrate),
        checked = vibrationEnabled,
        onCheckedChange = { vibrationEnabled = it }
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

    Text(
        text = "${stringResource(Res.string.settings_account_phone)}: $phoneNumber",
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
