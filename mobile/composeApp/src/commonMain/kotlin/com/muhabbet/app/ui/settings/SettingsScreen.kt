package com.muhabbet.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.RadioButton
import coil3.compose.AsyncImage
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.AuthRepository
import com.muhabbet.app.data.repository.MediaRepository
import com.muhabbet.app.platform.ImagePickerLauncher
import com.muhabbet.app.platform.PickedImage
import com.muhabbet.app.platform.compressImage
import com.muhabbet.app.platform.rememberImagePickerLauncher
import com.muhabbet.app.platform.rememberRestartApp
import com.muhabbet.app.ui.components.UserAvatar
import androidx.compose.ui.layout.ContentScale
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    authRepository: AuthRepository = koinInject(),
    mediaRepository: MediaRepository = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var displayName by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf(tokenStorage.getLanguage() ?: "tr") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val restartApp = rememberRestartApp()

    val profileUpdatedMsg = stringResource(Res.string.settings_profile_updated)
    val genericErrorMsg = stringResource(Res.string.error_generic)
    val photoUploadFailedMsg = stringResource(Res.string.profile_photo_failed)

    val imagePickerLauncher: ImagePickerLauncher = rememberImagePickerLauncher { picked: PickedImage? ->
        if (picked == null) return@rememberImagePickerLauncher
        scope.launch {
            isUploadingPhoto = true
            try {
                val compressed = compressImage(picked.bytes)
                val uploadResponse = mediaRepository.uploadImage(
                    bytes = compressed,
                    mimeType = "image/jpeg",
                    fileName = picked.fileName
                )
                authRepository.updateProfile(avatarUrl = uploadResponse.url)
                avatarUrl = uploadResponse.url
                snackbarHostState.showSnackbar(profileUpdatedMsg)
            } catch (_: Exception) {
                snackbarHostState.showSnackbar(photoUploadFailedMsg)
            }
            isUploadingPhoto = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            val profile = authRepository.getProfile()
            displayName = profile.displayName ?: ""
            about = profile.about ?: ""
            avatarUrl = profile.avatarUrl
        } catch (_: Exception) { }
        isLoading = false
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(Res.string.logout_confirm_title)) },
            text = { Text(stringResource(Res.string.logout_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text(
                        stringResource(Res.string.logout_confirm_yes),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(Res.string.logout_confirm_no))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
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
        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(32.dp))
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar with camera overlay
                Box(contentAlignment = Alignment.Center) {
                    UserAvatar(
                        avatarUrl = avatarUrl,
                        displayName = displayName,
                        size = 80.dp,
                        modifier = Modifier.clickable(enabled = !isUploadingPhoto) {
                            imagePickerLauncher.launch()
                        }
                    )
                    // Camera icon overlay
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
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Profile section
                Text(
                    text = stringResource(Res.string.settings_profile_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 64) displayName = it },
                    label = { Text(stringResource(Res.string.settings_display_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = about,
                    onValueChange = { if (it.length <= 140) about = it },
                    label = { Text(stringResource(Res.string.settings_about)) },
                    placeholder = { Text(stringResource(Res.string.settings_about_placeholder)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            try {
                                authRepository.updateProfile(
                                    displayName = displayName.ifBlank { null },
                                    about = about.ifBlank { null }
                                )
                                snackbarHostState.showSnackbar(profileUpdatedMsg)
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar(genericErrorMsg)
                            }
                            isSaving = false
                        }
                    },
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

                Spacer(Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // App section
                Text(
                    text = stringResource(Res.string.settings_app_section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))

                Text(
                    text = "${stringResource(Res.string.settings_version)}: 0.1.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                // Language section
                Text(
                    text = stringResource(Res.string.settings_language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            selectedLanguage = "tr"
                            tokenStorage.setLanguage("tr")
                            restartApp()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selectedLanguage == "tr",
                        onClick = {
                            selectedLanguage = "tr"
                            tokenStorage.setLanguage("tr")
                            restartApp()
                        }
                    )
                    Text(
                        text = stringResource(Res.string.settings_language_turkish),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable {
                            selectedLanguage = "en"
                            tokenStorage.setLanguage("en")
                            restartApp()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = selectedLanguage == "en",
                        onClick = {
                            selectedLanguage = "en"
                            tokenStorage.setLanguage("en")
                            restartApp()
                        }
                    )
                    Text(
                        text = stringResource(Res.string.settings_language_english),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Theme section
                Text(
                    text = stringResource(Res.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                var selectedTheme by remember { mutableStateOf(tokenStorage.getTheme() ?: "system") }
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
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { showLogoutDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(Res.string.settings_logout))
                }
            }
        }
    }
}
