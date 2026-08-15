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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muhabbet.app.crypto.E2EConfig
import com.muhabbet.app.data.repository.AuthRepository
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
    authRepository: AuthRepository = koinInject()
) {
    var isExporting by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var readReceiptsEnabled by remember { mutableStateOf(true) }
    var lastSeenVisibility by remember { mutableStateOf("everyone") }
    var profilePhotoVisibility by remember { mutableStateOf("everyone") }
    var aboutVisibility by remember { mutableStateOf("contacts") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val exportStartedMsg = stringResource(Res.string.privacy_export_started)
    val exportFailedMsg = stringResource(Res.string.privacy_export_failed)
    val deleteSuccessMsg = stringResource(Res.string.privacy_delete_success)
    val errorMsg = stringResource(Res.string.error_generic)

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
                        snackbarHostState.showSnackbar(deleteSuccessMsg)
                        onLogout()
                    } catch (_: Exception) {
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

            item {
                PrivacyVisibilityRow(
                    label = stringResource(Res.string.privacy_last_seen),
                    description = stringResource(Res.string.privacy_last_seen_desc),
                    selectedValue = lastSeenVisibility,
                    onValueChange = { lastSeenVisibility = it }
                )
            }

            item {
                PrivacyVisibilityRow(
                    label = stringResource(Res.string.privacy_profile_photo),
                    description = stringResource(Res.string.privacy_profile_photo_desc),
                    selectedValue = profilePhotoVisibility,
                    onValueChange = { profilePhotoVisibility = it }
                )
            }

            item {
                PrivacyVisibilityRow(
                    label = stringResource(Res.string.privacy_about),
                    description = stringResource(Res.string.privacy_about_desc),
                    selectedValue = aboutVisibility,
                    onValueChange = { aboutVisibility = it }
                )
            }

            item {
                SettingsSwitchRow(
                    title = stringResource(Res.string.settings_privacy_read_receipts),
                    subtitle = stringResource(Res.string.settings_privacy_read_receipts_subtitle),
                    checked = readReceiptsEnabled,
                    onCheckedChange = { readReceiptsEnabled = it }
                )
                MuhabbetDivider()
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

            // Informational, not a link: there is no blocked-contacts screen to open. This row used
            // to carry `.clickable { }` with an empty body, so it looked tappable and did nothing.
            // Blocking and unblocking happen from a person's profile; listing them centrally needs a
            // screen plus a repository (the backend's GET /moderation/blocks returns bare IDs, with
            // no way to resolve them to names), which is a feature, not a restyle.
            item {
                SettingsInfoRow(
                    title = stringResource(Res.string.privacy_blocked_contacts),
                    subtitle = stringResource(Res.string.privacy_blocked_contacts_desc),
                    icon = Muhabbet.icons.Block
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
                    subtitle = stringResource(Res.string.privacy_export_data_desc),
                    icon = Muhabbet.icons.Download,
                    loading = isExporting,
                    onClick = {
                        isExporting = true
                        scope.launch {
                            try {
                                authRepository.exportData()
                                snackbarHostState.showSnackbar(exportStartedMsg)
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar(exportFailedMsg)
                            }
                            isExporting = false
                        }
                    }
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
