package com.muhabbet.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.muhabbet.app.data.repository.PrivacyModeRepository
import com.muhabbet.app.platform.SecureScreenEffect
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.action_back
import com.muhabbet.composeapp.generated.resources.mahrem_app_lock
import com.muhabbet.composeapp.generated.resources.mahrem_app_lock_subtitle
import com.muhabbet.composeapp.generated.resources.mahrem_description
import com.muhabbet.composeapp.generated.resources.mahrem_hide_preview
import com.muhabbet.composeapp.generated.resources.mahrem_hide_preview_subtitle
import com.muhabbet.composeapp.generated.resources.mahrem_last_seen
import com.muhabbet.composeapp.generated.resources.mahrem_last_seen_subtitle
import com.muhabbet.composeapp.generated.resources.mahrem_pin_confirm_message
import com.muhabbet.composeapp.generated.resources.mahrem_pin_invalid
import com.muhabbet.composeapp.generated.resources.mahrem_pin_label
import com.muhabbet.composeapp.generated.resources.mahrem_pin_mismatch
import com.muhabbet.composeapp.generated.resources.mahrem_pin_set_message
import com.muhabbet.composeapp.generated.resources.mahrem_pin_set_title
import com.muhabbet.composeapp.generated.resources.mahrem_save
import com.muhabbet.composeapp.generated.resources.mahrem_screenshot_guard
import com.muhabbet.composeapp.generated.resources.mahrem_screenshot_guard_ios_note
import com.muhabbet.composeapp.generated.resources.mahrem_screenshot_guard_subtitle
import com.muhabbet.composeapp.generated.resources.mahrem_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * **Mahrem Mod (Privacy Mode)** settings — slice S1. Gated by
 * [com.muhabbet.app.config.PrivacyModeConfig] at the call site (the row is hidden in Settings when
 * the flag is OFF). Each toggle persists immediately through [PrivacyModeRepository]; no "save"
 * button. The screenshot-guard toggle, while ON, marks this very screen secure via
 * [SecureScreenEffect] (Android FLAG_SECURE; honest no-op on iOS).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MahremModScreen(
    onBack: () -> Unit,
    onLastSeenSettings: () -> Unit,
    privacyMode: PrivacyModeRepository = koinInject()
) {
    var hidePreview by remember { mutableStateOf(privacyMode.isPreviewHidden()) }
    var screenshotGuard by remember { mutableStateOf(privacyMode.isScreenshotGuardEnabled()) }
    var appLockEnabled by remember { mutableStateOf(privacyMode.isPinSet()) }
    var showPinDialog by remember { mutableStateOf(false) }

    // Honour the screenshot guard on this screen too.
    SecureScreenEffect(enabled = screenshotGuard)

    if (showPinDialog) {
        PinSetupDialog(
            onConfirm = { pin ->
                if (privacyMode.setPin(pin)) {
                    appLockEnabled = true
                    showPinDialog = false
                }
            },
            onDismiss = {
                showPinDialog = false
                // If the user backed out without setting a PIN, keep the toggle off.
                appLockEnabled = privacyMode.isPinSet()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.mahrem_title)) },
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MuhabbetSpacing.XLarge)
        ) {
            Text(
                text = stringResource(Res.string.mahrem_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(MuhabbetSpacing.XLarge))

            ToggleRow(
                title = stringResource(Res.string.mahrem_hide_preview),
                subtitle = stringResource(Res.string.mahrem_hide_preview_subtitle),
                checked = hidePreview,
                onCheckedChange = {
                    hidePreview = it
                    privacyMode.setPreviewHidden(it)
                }
            )

            Spacer(Modifier.height(MuhabbetSpacing.Large))
            HorizontalDivider()
            Spacer(Modifier.height(MuhabbetSpacing.Large))

            ToggleRow(
                title = stringResource(Res.string.mahrem_screenshot_guard),
                subtitle = stringResource(Res.string.mahrem_screenshot_guard_subtitle),
                checked = screenshotGuard,
                onCheckedChange = {
                    screenshotGuard = it
                    privacyMode.setScreenshotGuardEnabled(it)
                }
            )
            Text(
                text = stringResource(Res.string.mahrem_screenshot_guard_ios_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(MuhabbetSpacing.Large))
            HorizontalDivider()
            Spacer(Modifier.height(MuhabbetSpacing.Large))

            ToggleRow(
                title = stringResource(Res.string.mahrem_app_lock),
                subtitle = stringResource(Res.string.mahrem_app_lock_subtitle),
                checked = appLockEnabled,
                onCheckedChange = { wantOn ->
                    if (wantOn) {
                        // Reflect intent immediately; the dialog confirms or reverts it.
                        appLockEnabled = true
                        showPinDialog = true
                    } else {
                        privacyMode.clearPin()
                        appLockEnabled = false
                    }
                }
            )

            Spacer(Modifier.height(MuhabbetSpacing.Large))
            HorizontalDivider()
            Spacer(Modifier.height(MuhabbetSpacing.Large))

            // Last-seen / online visibility lives in the Privacy dashboard — surface a link to it.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLastSeenSettings() }
                    .padding(vertical = MuhabbetSpacing.Small)
            ) {
                Text(
                    text = stringResource(Res.string.mahrem_last_seen),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(Res.string.mahrem_last_seen_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val invalidMsg = stringResource(Res.string.mahrem_pin_invalid)
    val mismatchMsg = stringResource(Res.string.mahrem_pin_mismatch)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.mahrem_pin_set_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.mahrem_pin_set_message))
                Spacer(Modifier.height(MuhabbetSpacing.Medium))
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        if (it.length <= PrivacyModeRepository.PIN_MAX_LENGTH && it.all { c -> c.isDigit() }) {
                            pin = it
                            error = null
                        }
                    },
                    label = { Text(stringResource(Res.string.mahrem_pin_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(MuhabbetSpacing.Small))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = {
                        if (it.length <= PrivacyModeRepository.PIN_MAX_LENGTH && it.all { c -> c.isDigit() }) {
                            confirm = it
                            error = null
                        }
                    },
                    label = { Text(stringResource(Res.string.mahrem_pin_confirm_message)) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    !PrivacyModeRepository.isValidPin(pin) -> error = invalidMsg
                    pin != confirm -> error = mismatchMsg
                    else -> onConfirm(pin)
                }
            }) {
                Text(stringResource(Res.string.mahrem_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_back))
            }
        }
    )
}
