package com.muhabbet.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.muhabbet.app.data.repository.DeviceLinkRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.app.multidevice.MultiDeviceConfig
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.ConfirmDialog
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.LinkedDeviceResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.MuhabbetLoadingState

/**
 * Linked-devices management screen (Tier 2, NON-CRYPTO slice).
 *
 * Lists the account's active devices and lets the user revoke a companion or start a new link.
 * Entirely gated by [MultiDeviceConfig.ENABLED]: when OFF this screen renders nothing meaningful
 * (callers should not navigate here), preserving the single-device experience.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesScreen(
    onBack: () -> Unit,
    onLinkNewDevice: () -> Unit,
    repository: DeviceLinkRepository = koinInject()
) {
    var devices by remember { mutableStateOf<List<LinkedDeviceResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var pendingRevokeId by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val title = stringResource(Res.string.linked_devices_title)
    val emptyText = stringResource(Res.string.linked_devices_empty)
    val linkNewText = stringResource(Res.string.linked_devices_link_new)
    val revokedMsg = stringResource(Res.string.linked_devices_revoked)
    val revokeConfirm = stringResource(Res.string.linked_devices_revoke_confirm)
    val loadFailedMsg = stringResource(Res.string.error_load_failed)
    val actionFailedMsg = stringResource(Res.string.error_action_failed)

    suspend fun reload() {
        // Was runCatching { }.onSuccess { } with no onFailure: a rejected list left `devices` empty
        // and the screen said "no linked devices yet", which is exactly what it says when the
        // account really has none. Multi-device is server-flagged OFF, so 403 is the common answer.
        val failure = runCatchingCancellable { devices = repository.listDevices() }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        if (failure != null) {
            Log.e(TAG, "Failed to list linked devices", failure)
            snackbarHostState.showSnackbar(loadFailedMsg)
        }
    }

    LaunchedEffect(Unit) {
        if (MultiDeviceConfig.ENABLED) reload() else isLoading = false
    }

    MuhabbetScaffold(
        snackbarHostState = snackbarHostState,
        topBar = {
            MuhabbetTopBar(
                title = title,
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        floatingActionButton = {
            if (MultiDeviceConfig.ENABLED) {
                ExtendedFloatingActionButton(
                    onClick = onLinkNewDevice,
                    icon = { Icon(Muhabbet.icons.Add, contentDescription = null) },
                    text = { Text(linkNewText) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> MuhabbetLoadingState()
                devices.isEmpty() -> Text(
                    emptyText,
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(MuhabbetSpacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceRow(device = device, onRevoke = { pendingRevokeId = device.id })
                    }
                }
            }
        }
    }

    pendingRevokeId?.let { id ->
        ConfirmDialog(
            title = stringResource(Res.string.linked_devices_revoke),
            message = revokeConfirm,
            confirmLabel = stringResource(Res.string.linked_devices_revoke),
            dismissLabel = stringResource(Res.string.cancel),
            isDestructive = true,
            onConfirm = {
                val toRevoke = id
                pendingRevokeId = null
                scope.launch {
                    // Same omission on the write side: a rejected revoke dismissed the dialog and
                    // left the device in the list, reading as a tap that never registered.
                    runCatchingCancellable { repository.revokeDevice(toRevoke) }
                        .onSuccess {
                            reload()
                            snackbarHostState.showSnackbar(revokedMsg)
                        }
                        .onFailure { e ->
                            Log.e(TAG, "Failed to revoke device", e)
                            snackbarHostState.showSnackbar(actionFailedMsg)
                        }
                }
            },
            onDismiss = { pendingRevokeId = null }
        )
    }
}

@Composable
private fun DeviceRow(device: LinkedDeviceResponse, onRevoke: () -> Unit) {
    val primaryLabel = stringResource(Res.string.linked_devices_primary)
    val companionLabel = stringResource(Res.string.linked_devices_companion)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MuhabbetSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.displayName ?: device.platform,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(MuhabbetSpacing.XSmall))
                Text(
                    text = if (device.isPrimary) primaryLabel else companionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Only companion devices can be revoked from here; the primary cannot unlink itself.
            if (device.isCompanion && !device.isPrimary) {
                MuhabbetIconButton(
                    icon = Muhabbet.icons.Delete,
                    contentDescription = stringResource(Res.string.linked_devices_revoke),
                    onClick = onRevoke,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private const val TAG = "LinkedDevicesScreen"
