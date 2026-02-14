package com.muhabbet.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import com.muhabbet.app.ui.theme.LocalSemanticColors
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.ui.components.UserAvatar
import com.muhabbet.shared.dto.MutualGroupResponse
import com.muhabbet.shared.dto.UserProfileDetailResponse
import com.muhabbet.app.util.DateTimeFormatter
import kotlinx.coroutines.launch
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: String,
    contactName: String? = null,
    conversationId: String? = null,
    onBack: () -> Unit,
    onMessageClick: (() -> Unit)? = null,
    onGroupClick: ((conversationId: String, name: String) -> Unit)? = null,
    onSharedMediaClick: ((conversationId: String) -> Unit)? = null,
    conversationRepository: ConversationRepository = koinInject()
) {
    var profile by remember { mutableStateOf<UserProfileDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val errorMsg = stringResource(Res.string.profile_load_failed)
    val callComingSoonMsg = stringResource(Res.string.call_coming_soon)

    LaunchedEffect(userId) {
        try {
            profile = conversationRepository.getUserProfileDetail(userId)
        } catch (e: Exception) {
            error = errorMsg
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.profile_view_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
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
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        } else if (profile != null) {
            val p = profile!!
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                // Header: avatar + name + status
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = MuhabbetSpacing.XLarge),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        UserAvatar(
                            avatarUrl = p.avatarUrl,
                            displayName = p.displayName ?: "?",
                            size = 96.dp
                        )

                        Spacer(Modifier.height(MuhabbetSpacing.Large))

                        Text(
                            text = p.displayName ?: stringResource(Res.string.unknown),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )

                        // Show contact name if different from display name
                        if (contactName != null && contactName != p.displayName) {
                            Text(
                                text = "~${contactName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (p.isOnline) {
                            Text(
                                text = stringResource(Res.string.chat_online),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalSemanticColors.current.statusOnline
                            )
                        } else if (p.lastSeenAt != null) {
                            val time = formatLastSeen(p.lastSeenAt!!)
                            Text(
                                text = stringResource(Res.string.profile_last_seen, time),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Action buttons row
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.XXLarge, vertical = MuhabbetSpacing.Small),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (onMessageClick != null) {
                            ProfileActionButton(
                                icon = Icons.AutoMirrored.Filled.Message,
                                label = stringResource(Res.string.profile_action_message),
                                onClick = onMessageClick
                            )
                        }
                        ProfileActionButton(
                            icon = Icons.Default.Call,
                            label = stringResource(Res.string.profile_action_call),
                            onClick = { scope.launch { snackbarHostState.showSnackbar(callComingSoonMsg) } }
                        )
                    }
                    Spacer(Modifier.height(MuhabbetSpacing.Small))
                    HorizontalDivider()
                }

                // Phone number
                item {
                    ProfileInfoRow(
                        label = stringResource(Res.string.profile_phone),
                        value = p.phoneNumber
                    )
                    HorizontalDivider()
                }

                // About
                item {
                    ProfileInfoRow(
                        label = stringResource(Res.string.profile_about_label),
                        value = p.about ?: stringResource(Res.string.profile_no_about)
                    )
                    HorizontalDivider()
                }

                // Shared media count
                if (p.sharedMediaCount > 0) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = conversationId != null) {
                                    conversationId?.let { onSharedMediaClick?.invoke(it) }
                                }
                                .padding(horizontal = MuhabbetSpacing.Large, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(MuhabbetSpacing.Medium))
                            Text(
                                text = stringResource(Res.string.profile_shared_media, p.sharedMediaCount),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        HorizontalDivider()
                    }
                }

                // Mutual groups section
                if (p.mutualGroups.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Large, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(MuhabbetSpacing.Medium))
                            Text(
                                text = stringResource(Res.string.profile_mutual_groups, p.mutualGroups.size),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    items(p.mutualGroups, key = { it.conversationId }) { group ->
                        MutualGroupItem(
                            group = group,
                            onClick = { onGroupClick?.invoke(group.conversationId, group.name) }
                        )
                    }

                    item { HorizontalDivider() }
                }

                // Bottom spacing
                item { Spacer(Modifier.height(MuhabbetSpacing.XLarge)) }
            }
        }
    }
}

@Composable
private fun ProfileActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(MuhabbetSpacing.Medium)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(MuhabbetSpacing.XSmall))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(MuhabbetSpacing.XSmall))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun MutualGroupItem(
    group: MutualGroupResponse,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUrl = group.avatarUrl,
            displayName = group.name,
            size = 40.dp,
            isGroup = true
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(Res.string.group_participant_count, group.memberCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun firstGrapheme(text: String): String {
    if (text.isEmpty()) return "?"
    val ch = text[0]
    if (ch.code < 0x80) return ch.uppercase()
    if (ch.isHighSurrogate() && text.length > 1 && text[1].isLowSurrogate()) {
        var end = 2
        while (end < text.length) {
            val c = text[end]
            if (c == '\u200D') {
                end++
                if (end < text.length) {
                    end++
                    if (end < text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
                        end++
                    }
                }
            } else if (c == '\uFE0F' || c == '\uFE0E') {
                end++
            } else if (c == '\uD83C' && end + 1 < text.length) {
                val low = text[end + 1]
                if (low.code in 0xDFFB..0xDFFF) {
                    end += 2
                } else break
            } else break
        }
        return text.substring(0, end)
    }
    return ch.toString()
}

private fun formatLastSeen(lastSeenStr: String): String =
    DateTimeFormatter.formatLastSeen(lastSeenStr)
