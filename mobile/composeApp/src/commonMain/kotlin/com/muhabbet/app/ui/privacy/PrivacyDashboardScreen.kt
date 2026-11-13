package com.muhabbet.app.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muhabbet.app.crypto.E2EConfig
import com.muhabbet.app.data.local.PrivacySettingsController
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.data.repository.DataExportRepository
import com.muhabbet.app.platform.DocumentSaveResult
import com.muhabbet.app.platform.rememberDocumentSaver
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import com.muhabbet.app.util.Log
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.ConfirmDialog
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetDivider
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.SectionHeader
import com.muhabbet.designsystem.components.SettingsInfoRow
import com.muhabbet.designsystem.components.SettingsNavRow
import com.muhabbet.designsystem.components.SettingsSwitchRow
import com.muhabbet.designsystem.components.MuhabbetChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onBlockedUsers: () -> Unit,
    authRepository: AuthRepository = koinInject(),
    dataExportRepository: DataExportRepository = koinInject(),
    privacySettings: PrivacySettingsController = koinInject()
) {
    var exportState by remember { mutableStateOf<DataExportState>(DataExportState.Idle) }
    fun dispatchExport(event: DataExportEvent) { exportState = reduceDataExport(exportState, event) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Server-backed, shared with Settings → Gizlilik. Every control on this screen used to be a
    // `remember { mutableStateOf(...) }` seeded with the most permissive option: nothing loaded on
    // open, nothing saved on change, and reopening silently reported "everyone" to a user who may
    // have chosen "nobody". On the screen that carries the app's KVKK claim, that is the defect.
    val settings by privacySettings.settings.collectAsState()
    var loadFailed by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val exportSavedMsg = stringResource(Res.string.privacy_export_saved)
    val exportFailedMsg = stringResource(Res.string.privacy_export_failed)
    val errorMsg = stringResource(Res.string.error_generic)
    val saveFailedMsg = stringResource(Res.string.privacy_settings_save_failed)

    LaunchedEffect(Unit) { loadFailed = privacySettings.load().isFailure }

    // The last step of the export: the user decides where the file goes, or backs out.
    val documentSaver = rememberDocumentSaver(mimeType = EXPORT_MIME_TYPE) { result ->
        dispatchExport(
            when (result) {
                DocumentSaveResult.SAVED -> DataExportEvent.Saved
                DocumentSaveResult.CANCELLED -> DataExportEvent.SaveCancelled
                DocumentSaveResult.FAILED -> DataExportEvent.Failed
            }
        )
    }

    // Outcomes are reported from the state, once, then acknowledged. Done and Failed are not busy,
    // so the row's spinner is already gone while the snackbar's ~4 s suspension runs, and a
    // cancelled picker reports nothing at all. Acknowledged comes after the snackbar because this
    // effect is keyed on the state: changing it first would cancel the snackbar it is showing.
    LaunchedEffect(exportState) {
        val message = when (exportState) {
            DataExportState.Done -> exportSavedMsg
            DataExportState.Failed -> exportFailedMsg
            else -> return@LaunchedEffect
        }
        snackbarHostState.showSnackbar(message)
        dispatchExport(DataExportEvent.Acknowledged)
    }

    fun startExport() {
        if (exportState.isBusy) return
        dispatchExport(DataExportEvent.Started)
        scope.launch {
            try {
                val document = dataExportRepository.exportAll { page ->
                    dispatchExport(DataExportEvent.PageStarted(page))
                }
                val bytes = dataExportRepository.encode(document).encodeToByteArray()
                dispatchExport(DataExportEvent.Assembled)
                val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                documentSaver.save(dataExportFileName(today.toString()), bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Data export failed", e)
                dispatchExport(DataExportEvent.Failed)
            }
        }
    }

    fun save(
        readReceiptsEnabled: Boolean? = null,
        onlineStatusVisibility: String? = null,
        aboutVisibility: String? = null
    ) {
        scope.launch {
            val failed = privacySettings.update(
                readReceiptsEnabled = readReceiptsEnabled,
                onlineStatusVisibility = onlineStatusVisibility,
                aboutVisibility = aboutVisibility
            ).isFailure
            if (failed) snackbarHostState.showSnackbar(saveFailedMsg)
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.privacy_delete_confirm_title),
            message = stringResource(Res.string.privacy_delete_confirm_message),
            confirmLabel = stringResource(Res.string.privacy_delete_account),
            dismissLabel = stringResource(Res.string.cancel),
            onConfirm = {
                showDeleteDialog = false
                scope.launch {
                    try {
                        authRepository.deleteAccount()
                        // Straight out (#780). This used to await a success snackbar first, and
                        // `showSnackbar` suspends until it is dismissed — about four seconds spent
                        // inside an account that no longer exists, with every control still live.
                        onLogout()
                    } catch (e: Exception) {
                        // deleteAccount() cleared the tokens before checking anything, so a 500 used
                        // to log the user out and tell them their account was gone. The snackbar was
                        // already here; the log was not, and this is a KVKK path worth a record.
                        Log.e(TAG, "Account deletion failed", e)
                        snackbarHostState.showSnackbar(errorMsg)
                    }
                }
            },
            onDismiss = { showDeleteDialog = false },
            isDestructive = true
        )
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.privacy_dashboard_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Visibility section
            item {
                SectionHeader(
                    icon = Muhabbet.icons.Visible,
                    title = stringResource(Res.string.privacy_visibility_section)
                )
            }

            // Three controls, not four. "Profil Fotoğrafı" is gone: there is no
            // profile_photo_visibility column, the PATCH body has no such field, and avatarUrl is
            // returned to every caller unconditionally — so the picker had nothing to write to and
            // nothing to affect. Removed rather than left looking adjustable; it comes back with
            // the column, the request field and the gate on the avatar, together.
            //
            // "Son Görülme" sends onlineStatusVisibility, which is the one field that gates both
            // presence and last-seen server-side.
            val current = settings
            when {
                current != null -> {
                    item {
                        PrivacyVisibilityRow(
                            label = stringResource(Res.string.privacy_last_seen),
                            description = stringResource(Res.string.privacy_last_seen_desc),
                            selectedValue = current.onlineStatusVisibility,
                            onValueChange = { save(onlineStatusVisibility = it) }
                        )
                    }

                    item {
                        PrivacyVisibilityRow(
                            label = stringResource(Res.string.privacy_about),
                            description = stringResource(Res.string.privacy_about_desc),
                            selectedValue = current.aboutVisibility,
                            onValueChange = { save(aboutVisibility = it) }
                        )
                    }

                    item {
                        SettingsSwitchRow(
                            title = stringResource(Res.string.settings_privacy_read_receipts),
                            subtitle = stringResource(Res.string.settings_privacy_read_receipts_subtitle),
                            checked = current.readReceiptsEnabled,
                            onCheckedChange = { save(readReceiptsEnabled = it) }
                        )
                        MuhabbetDivider()
                    }
                }

                loadFailed -> item {
                    SettingsInfoRow(
                        title = stringResource(Res.string.privacy_settings_load_failed),
                        icon = Muhabbet.icons.Info
                    )
                    MuhabbetDivider()
                }

                else -> item {
                    SettingsInfoRow(title = stringResource(Res.string.privacy_settings_loading))
                    MuhabbetDivider()
                }
            }

            // Security section
            item {
                SectionHeader(
                    icon = Muhabbet.icons.Lock,
                    title = stringResource(Res.string.privacy_security_section)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(MuhabbetSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // HONEST about transport: E2E is OFF in production (plaintext under TLS).
                        // Don't show a padlock or claim end-to-end encryption when E2EConfig.ENABLED
                        // is false — state the truthful TLS-in-transit posture instead.
                        Icon(
                            if (E2EConfig.ENABLED) Muhabbet.icons.Lock else Muhabbet.icons.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(MuhabbetSizes.IconLarge)
                        )
                        Spacer(Modifier.width(MuhabbetSpacing.Medium))
                        Text(
                            text = if (E2EConfig.ENABLED) {
                                stringResource(Res.string.privacy_e2e_info)
                            } else {
                                stringResource(Res.string.privacy_transport_info)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // #613: was `SettingsInfoRow` with `.clickable { }` and an empty body — it looked
            // tappable and did nothing, because there was nowhere to send the tap. There is now:
            // `ModerationController.getBlockedUsers()` resolves a name and a face per block
            // server-side, so `BlockedUsersScreen` has something to render besides bare UUIDs.
            item {
                SettingsNavRow(
                    title = stringResource(Res.string.privacy_blocked_contacts),
                    subtitle = stringResource(Res.string.privacy_blocked_contacts_desc),
                    icon = Muhabbet.icons.Block,
                    onClick = onBlockedUsers
                )
                MuhabbetDivider()
            }

            // My Data section
            item {
                SectionHeader(
                    icon = Muhabbet.icons.Download,
                    title = stringResource(Res.string.privacy_data_section)
                )
            }

            item {
                SettingsNavRow(
                    title = stringResource(Res.string.privacy_export_data),
                    subtitle = when (val state = exportState) {
                        is DataExportState.Fetching -> stringResource(Res.string.privacy_export_progress, state.page)
                        DataExportState.Saving -> stringResource(Res.string.privacy_export_saving)
                        else -> stringResource(Res.string.privacy_export_data_desc)
                    },
                    icon = Muhabbet.icons.Download,
                    loading = exportState.isBusy,
                    onClick = ::startExport
                )
            }

            item {
                SettingsNavRow(
                    title = stringResource(Res.string.privacy_delete_account),
                    subtitle = stringResource(Res.string.privacy_delete_account_desc),
                    icon = Muhabbet.icons.Delete,
                    destructive = true,
                    onClick = { showDeleteDialog = true }
                )
                MuhabbetDivider()
            }

            // KVKK Rights section
            item {
                SectionHeader(
                    icon = Muhabbet.icons.Moderation,
                    title = stringResource(Res.string.privacy_kvkk_section)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(MuhabbetSpacing.Medium)) {
                        Text(
                            text = stringResource(Res.string.privacy_kvkk_info),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(MuhabbetSpacing.Medium))
                        KvkkRight(stringResource(Res.string.privacy_kvkk_right_access))
                        KvkkRight(stringResource(Res.string.privacy_kvkk_right_rectification))
                        KvkkRight(stringResource(Res.string.privacy_kvkk_right_erasure))
                        KvkkRight(stringResource(Res.string.privacy_kvkk_right_portability))
                    }
                }
            }

            // Bottom spacing
            item { Spacer(Modifier.height(MuhabbetSpacing.XXLarge)) }
        }
    }
}

@Composable
private fun PrivacyVisibilityRow(
    label: String,
    description: String,
    selectedValue: String,
    onValueChange: (String) -> Unit
) {
    val options = listOf(
        "everyone" to stringResource(Res.string.privacy_visibility_everyone),
        "contacts" to stringResource(Res.string.privacy_visibility_contacts),
        "nobody" to stringResource(Res.string.privacy_visibility_nobody)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(MuhabbetSpacing.XSmall))
        Row(
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.Small)
        ) {
            options.forEach { (key, label) ->
                val isSelected = selectedValue == key
                MuhabbetChip(
                    label = label,
                    selected = isSelected,
                    onClick = { onValueChange(key) },
                    leadingIcon = if (isSelected) {
                        { Icon(Muhabbet.icons.Sent, contentDescription = null, modifier = Modifier.size(MuhabbetSizes.IconSmall)) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun KvkkRight(text: String) {
    Row(
        modifier = Modifier.padding(vertical = MuhabbetSpacing.XSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Muhabbet.icons.Sent,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(MuhabbetSizes.IconSmall)
        )
        Spacer(Modifier.width(MuhabbetSpacing.Small))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private const val TAG = "PrivacyDashboardScreen"

private const val EXPORT_MIME_TYPE = "application/json"
