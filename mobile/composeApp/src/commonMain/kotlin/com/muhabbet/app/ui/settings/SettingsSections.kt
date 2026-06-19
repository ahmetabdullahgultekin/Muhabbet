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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.ui.components.UserAvatar
import com.muhabbet.app.ui.theme.MuhabbetElevation
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.StorageUsageResponse
import org.jetbrains.compose.resources.stringResource

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
    Box(contentAlignment = Alignment.Center) {
        UserAvatar(
            avatarUrl = avatarUrl,
            displayName = displayName,
            size = 80.dp,
            modifier = Modifier.clickable(enabled = !isUploadingPhoto) { onPickPhoto() }
        )
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
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(stringResource(Res.string.settings_save))
        }
    }
}

/**
 * Reusable settings navigation row: an icon + label inside a clickable [Surface].
 * Used by every "open sub-screen" entry in [SettingsScreen].
 */
@Composable
internal fun SettingsNavRow(
    label: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        tonalElevation = MuhabbetElevation.Level1,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MuhabbetSpacing.Medium, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Medium)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
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
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
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
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable {
                    selectedLanguage = key
                    tokenStorage.setLanguage(key)
                    restartApp()
                }
                .padding(vertical = MuhabbetSpacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
        ) {
            RadioButton(
                selected = selectedLanguage == key,
                onClick = {
                    selectedLanguage = key
                    tokenStorage.setLanguage(key)
                    restartApp()
                }
            )
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
internal fun ThemeSection(tokenStorage: TokenStorage, restartApp: () -> Unit) {
    var selectedTheme by remember { mutableStateOf(tokenStorage.getTheme() ?: "system") }
    SettingsSectionTitle(stringResource(Res.string.settings_theme))
    Spacer(Modifier.height(MuhabbetSpacing.Small))

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
}

@Composable
internal fun PrivacySection() {
    SettingsSectionTitle(stringResource(Res.string.settings_privacy_section))
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
        Switch(
            checked = readReceiptsEnabled,
            onCheckedChange = { readReceiptsEnabled = it }
        )
    }
}

@Composable
internal fun NotificationsSection() {
    SettingsSectionTitle(stringResource(Res.string.settings_notifications_section))
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
        Switch(
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
        Switch(
            checked = vibrationEnabled,
            onCheckedChange = { vibrationEnabled = it }
        )
    }
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
