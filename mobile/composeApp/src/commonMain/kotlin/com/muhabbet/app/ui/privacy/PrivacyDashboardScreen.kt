package com.muhabbet.app.ui.privacy

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.ui.components.ConfirmDialog
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.privacy_dashboard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Visibility section
            item {
                SectionHeader(
                    icon = Icons.Default.Visibility,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
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
                HorizontalDivider()
            }

            // Security section
            item {
                SectionHeader(
                    icon = Icons.Default.Lock,
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
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(MuhabbetSpacing.Medium))
                        Text(
                            text = stringResource(Res.string.privacy_e2e_info),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { }
                        .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(MuhabbetSpacing.Medium))
                    Column {
                        Text(
                            text = stringResource(Res.string.privacy_blocked_contacts),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(Res.string.privacy_blocked_contacts_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }

            // My Data section
            item {
                SectionHeader(
                    icon = Icons.Default.Download,
                    title = stringResource(Res.string.privacy_data_section)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isExporting) {
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
                        .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(MuhabbetSpacing.Medium))
                    Column {
                        Text(
                            text = stringResource(Res.string.privacy_export_data),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(Res.string.privacy_export_data_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDeleteDialog = true }
                        .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(MuhabbetSpacing.Medium))
                    Column {
                        Text(
                            text = stringResource(Res.string.privacy_delete_account),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(Res.string.privacy_delete_account_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }

            // KVKK Rights section
            item {
                SectionHeader(
                    icon = Icons.Default.Gavel,
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
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
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
                androidx.compose.material3.FilterChip(
                    selected = isSelected,
                    onClick = { onValueChange(key) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    leadingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun KvkkRight(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(MuhabbetSpacing.Small))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
