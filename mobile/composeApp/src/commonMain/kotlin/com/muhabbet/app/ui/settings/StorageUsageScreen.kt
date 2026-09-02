package com.muhabbet.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.platform.rememberDeviceMediaCache
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.formatBytes
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.ConfirmDialog
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.SettingsInfoRow
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.StorageUsageResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val TAG = "StorageUsageScreen"

/**
 * Where the Storage card in Settings now leads (#546), and the first screen in the app that
 * separates **what is on the phone** from **what is on the server**.
 *
 * ## Why that split is the whole screen
 *
 * The card this came from showed four numbers from `GET /api/v1/media/storage`, which sums
 * `media_files` rows by `uploader_id` — bytes sitting in MinIO because this user once *sent* them.
 * The issue observed all four reading `0 B (0)` and asked whether that was a bug. It is not: those
 * numbers are correct and they are also not an answer to the question people open a storage screen
 * to ask. Nobody clears space on their phone by deleting something from a server, and this account
 * had simply sent nothing.
 *
 * What actually occupies the device is the copies the app downloaded — Coil's disk cache, plus the
 * camera, voice and transcription temp files that are written before an upload and never cleaned up
 * afterwards. That is [rememberDeviceMediaCache], it is measured here, and clearing it is the one
 * action this screen offers. It is safe by construction: everything in that directory can be
 * fetched again, and nothing outside it is touched (see `DeviceMediaCache`).
 *
 * ## What was deliberately left out, and why
 *
 * - **Per-conversation breakdown**, the view the issue names as the one that gets used. It cannot be
 *   built from what the server records. `media_files` *has* a `conversation_id` column — from V1 —
 *   but nothing has ever written to it: the domain model has no such field and no upload command
 *   carries one, so every row is NULL. Delivering this means changing the upload API, both clients,
 *   and accepting that all historical media stays unattributable. Reconstructing it instead by
 *   matching `messages.media_url` against `media_files.file_key` is the substring-matching mistake
 *   #541 and #679 were both filed about, and here it would decide what to delete.
 * - **Server-side deletion.** #541 established that blobs are never deleted server-side and that
 *   doing it correctly needs the media *id* on the message, not a URL parsed for a key. That work
 *   landed for the view-once burn path only. A delete button here would be either dishonest or
 *   dangerous, and the screen says so rather than omitting the subject.
 * - **"Delete anything older than N months"** and per-type selection within a chat, both of which
 *   depend on the breakdown above existing first.
 *
 * The cuts are stated on screen, not just here: a user looking for the per-chat list should find out
 * that it does not exist yet rather than concluding they cannot find it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageUsageScreen(
    onBack: () -> Unit,
    mediaRepository: MediaRepository = koinInject()
) {
    val cache = rememberDeviceMediaCache()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Null while measuring — the screen says "calculating" rather than showing 0 B, which is also a
    // real answer and would be indistinguishable from it.
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var serverUsage by remember { mutableStateOf<StorageUsageResponse?>(null) }
    var serverLoading by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }
    // Set by the clear action, consumed by the effect below. The snackbar text is a formatted
    // string resource, and `stringResource` is @Composable — so the amount travels through state
    // and the sentence is built in composition, rather than the text being assembled inside
    // `scope.launch` where the resource cannot be read.
    var freedBytes by remember { mutableStateOf<Long?>(null) }

    val freedMessage = stringResource(Res.string.storage_freed, formatBytes(freedBytes ?: 0L))
    LaunchedEffect(freedBytes) {
        if (freedBytes == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(freedMessage)
        freedBytes = null
    }

    LaunchedEffect(Unit) {
        cacheBytes = cache.sizeBytes()
    }

    LaunchedEffect(Unit) {
        runCatchingCancellable { serverUsage = mediaRepository.getStorageUsage() }
            .onFailure { Log.e(TAG, "Failed to load server storage usage", it) }
        serverLoading = false
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.storage_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MuhabbetSpacing.XLarge)
        ) {
            SettingsSectionTitle(stringResource(Res.string.storage_device_section))
            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            SettingsInfoRow(
                title = stringResource(Res.string.storage_device_cache),
                subtitle = stringResource(Res.string.storage_device_cache_subtitle),
                value = cacheBytes?.let(::formatBytes)
                    ?: stringResource(Res.string.storage_device_measuring),
                icon = Muhabbet.icons.Image
            )

            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            Button(
                onClick = { showClearDialog = true },
                // Disabled while measuring and when there is nothing to free: a button that reports
                // "freed 0 B" is a button that looks broken.
                enabled = (cacheBytes ?: 0L) > 0L,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(Res.string.storage_free_up))
            }

            Spacer(Modifier.height(MuhabbetSpacing.XLarge))
            HorizontalDivider()
            Spacer(Modifier.height(MuhabbetSpacing.Large))

            SettingsSectionTitle(stringResource(Res.string.storage_server_section))
            Spacer(Modifier.height(MuhabbetSpacing.Small))
            StorageNote(stringResource(Res.string.storage_server_explanation))
            Spacer(Modifier.height(MuhabbetSpacing.Medium))

            // The same card the Settings screen shows, reused rather than reimplemented — one place
            // decides how these four numbers are labelled and coloured.
            StorageSection(storageLoading = serverLoading, storageUsage = serverUsage)

            Spacer(Modifier.height(MuhabbetSpacing.Medium))
            StorageNote(stringResource(Res.string.storage_server_not_deletable))
            Spacer(Modifier.height(MuhabbetSpacing.Small))
            StorageNote(stringResource(Res.string.storage_no_per_chat))
        }
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.storage_free_up_confirm_title),
            message = stringResource(Res.string.storage_free_up_confirm_body),
            confirmLabel = stringResource(Res.string.storage_free_up),
            dismissLabel = stringResource(Res.string.cancel),
            isDestructive = true,
            onDismiss = { showClearDialog = false },
            onConfirm = {
                showClearDialog = false
                scope.launch {
                    val freed = cache.clear()
                    // Re-measured rather than assumed to be zero: the OS may hold a file open, and
                    // reporting an empty cache that is not empty is the kind of small lie that makes
                    // a user press the button again and distrust the screen.
                    cacheBytes = cache.sizeBytes()
                    freedBytes = freed
                }
            }
        )
    }
}

@Composable
private fun StorageNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth()
    )
}
