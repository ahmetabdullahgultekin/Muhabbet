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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
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
import com.muhabbet.app.multidevice.MultiDeviceConfig
import com.muhabbet.app.ui.components.ConfirmDialog
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.LinkedDeviceResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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

    suspend fun reload() {
        runCatching { repository.listDevices() }
            .onSuccess { devices = it }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        if (MultiDeviceConfig.ENABLED) reload() else isLoading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = {
            if (MultiDeviceConfig.ENABLED) {
                ExtendedFloatingActionButton(
                    onClick = onLinkNewDevice,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(linkNewText) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
            isDestructive = true,
            onConfirm = {
                val toRevoke = id
                pendingRevokeId = null
                scope.launch {
                    runCatching { repository.revokeDevice(toRevoke) }
                        .onSuccess {
                            reload()
                            snackbarHostState.showSnackbar(revokedMsg)
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
                IconButton(onClick = onRevoke) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.linked_devices_revoke),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
