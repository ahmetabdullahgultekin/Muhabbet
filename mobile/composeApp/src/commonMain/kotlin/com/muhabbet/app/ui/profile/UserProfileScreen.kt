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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import androidx.compose.material3.SnackbarHostState
import com.muhabbet.app.crypto.E2EConfig
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.ModerationRepository
import com.muhabbet.designsystem.components.ConfirmDialog
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.app.ui.chat.MediaViewer
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.shared.dto.MutualGroupResponse
import com.muhabbet.shared.dto.UserProfileDetailResponse
import kotlinx.coroutines.launch
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.modifier.pressable
import androidx.compose.foundation.shape.CircleShape
import com.muhabbet.app.ui.components.rememberRelativeDayLabels

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
    conversationRepository: ConversationRepository = koinInject(),
    moderationRepository: ModerationRepository = koinInject()
) {
    var profile by remember { mutableStateOf<UserProfileDetailResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    // Null until the block-status check resolves, so the row does not flash "Block" for someone
    // already blocked while the request is in flight.
    var isBlocked by remember { mutableStateOf<Boolean?>(null) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showUnblockDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    // Only ever set true when profile.avatarUrl is non-null — see the render site below.
    var showPhotoViewer by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val relativeDayLabels = rememberRelativeDayLabels()
    val scope = rememberCoroutineScope()

    val errorMsg = stringResource(Res.string.profile_load_failed)
    val callComingSoonMsg = stringResource(Res.string.call_coming_soon)
    val blockSuccessMsg = stringResource(Res.string.profile_block_success)
    val blockFailedMsg = stringResource(Res.string.profile_block_failed)
    val unblockSuccessMsg = stringResource(Res.string.profile_unblock_success)
    val unblockFailedMsg = stringResource(Res.string.profile_unblock_failed)
    val reportSuccessMsg = stringResource(Res.string.profile_report_success)
    val reportFailedMsg = stringResource(Res.string.profile_report_failed)

    LaunchedEffect(userId) {
        runCatchingCancellable { profile = conversationRepository.getUserProfileDetail(userId) }
            .onFailure { e ->
                Log.e(TAG, "Failed to load user profile", e)
                error = errorMsg
            }
        isLoading = false
        // Independent of the profile load: a failure here should not block the rest of the
        // screen, it should just leave the Block/Report row at its safe default (offer "Block").
        runCatchingCancellable { isBlocked = moderationRepository.isBlocked(userId) }
            .onFailure { e -> Log.w(TAG, "Could not check block status for $userId: ${e.message}") }
    }

    if (showBlockDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.profile_block),
            message = stringResource(Res.string.profile_block_confirm),
            confirmLabel = stringResource(Res.string.profile_block),
            dismissLabel = stringResource(Res.string.cancel),
            onConfirm = {
                showBlockDialog = false
                scope.launch {
                    runCatchingCancellable { moderationRepository.blockUser(userId) }
                        .onSuccess {
                            isBlocked = true
                            snackbarHostState.showSnackbar(blockSuccessMsg)
                        }
                        .onFailure { e ->
                            Log.e(TAG, "Failed to block $userId", e)
                            snackbarHostState.showSnackbar(blockFailedMsg)
                        }
                }
            },
            onDismiss = { showBlockDialog = false },
            isDestructive = true
        )
    }

    if (showUnblockDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.profile_unblock),
            message = stringResource(Res.string.profile_unblock_confirm),
            confirmLabel = stringResource(Res.string.profile_unblock),
            dismissLabel = stringResource(Res.string.cancel),
            onConfirm = {
                showUnblockDialog = false
                scope.launch {
                    runCatchingCancellable { moderationRepository.unblockUser(userId) }
                        .onSuccess {
                            isBlocked = false
                            snackbarHostState.showSnackbar(unblockSuccessMsg)
                        }
                        .onFailure { e ->
                            Log.e(TAG, "Failed to unblock $userId", e)
                            snackbarHostState.showSnackbar(unblockFailedMsg)
                        }
                }
            },
            onDismiss = { showUnblockDialog = false }
        )
    }

    if (showReportDialog) {
        ConfirmDialog(
            title = stringResource(Res.string.profile_report),
            message = stringResource(Res.string.profile_report_confirm),
            confirmLabel = stringResource(Res.string.profile_report),
            dismissLabel = stringResource(Res.string.cancel),
            onConfirm = {
                showReportDialog = false
                scope.launch {
                    runCatchingCancellable {
                        moderationRepository.reportUser(reportedUserId = userId)
                    }
                        .onSuccess { snackbarHostState.showSnackbar(reportSuccessMsg) }
                        .onFailure { e ->
                            Log.e(TAG, "Failed to report $userId", e)
                            snackbarHostState.showSnackbar(reportFailedMsg)
                        }
                }
            },
            onDismiss = { showReportDialog = false },
            isDestructive = true
        )
    }

    if (showPhotoViewer) {
        profile?.avatarUrl?.let { url ->
            MediaViewer(image = url, onDismiss = { showPhotoViewer = false })
        }
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.profile_view_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (isLoading) {
            MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
        } else if (error != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "", color = MaterialTheme.colorScheme.error)
            }
        } else {
            val p = profile ?: return@MuhabbetScaffold
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
                            size = MuhabbetSizes.AvatarHero,
                            // No photo means no navigation: a full-screen gradient isn't worth it (#615).
                            modifier = if (p.avatarUrl != null) {
                                Modifier.pressable(shape = CircleShape) { showPhotoViewer = true }
                            } else {
                                Modifier
                            }
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
                                text = stringResource(Res.string.profile_contact_name) + ": $contactName",
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
                        } else {
                            val lastSeen = p.lastSeenAt
                            if (lastSeen != null) {
                                val time = DateTimeFormatter.formatLastSeen(lastSeen, relativeDayLabels)
                                Text(
                                    text = stringResource(Res.string.profile_last_seen, time),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                                icon = Muhabbet.icons.NewMessage,
                                label = stringResource(Res.string.profile_action_message),
                                onClick = onMessageClick
                            )
                        }
                        ProfileActionButton(
                            icon = Muhabbet.icons.CallStart,
                            label = stringResource(Res.string.profile_action_call),
                            onClick = { scope.launch { snackbarHostState.showSnackbar(callComingSoonMsg) } }
                        )
                    }
                    Spacer(Modifier.height(MuhabbetSpacing.Small))
                    HorizontalDivider()
                }

                // Encryption badge — HONEST about the actual transport.
                // E2E is OFF in production (plaintext under TLS), so do NOT show a padlock or claim
                // end-to-end encryption when E2EConfig.ENABLED is false. Show a truthful TLS state.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (E2EConfig.ENABLED) Muhabbet.icons.Lock else Muhabbet.icons.Info,
                            contentDescription = null,
                            tint = if (E2EConfig.ENABLED) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(MuhabbetSizes.IconLarge)
                        )
                        Spacer(Modifier.width(MuhabbetSpacing.Medium))
                        Text(
                            text = if (E2EConfig.ENABLED) {
                                stringResource(Res.string.profile_encrypted)
                            } else {
                                stringResource(Res.string.profile_transport_encrypted)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }

                // Phone number — only rendered when the backend exposes it (own profile).
                // Foreign-user lookups return null for privacy (KVKK), so the row is hidden.
                p.phoneNumber?.let { phone ->
                    item {
                        ProfileInfoRow(
                            label = stringResource(Res.string.profile_phone),
                            value = phone
                        )
                        HorizontalDivider()
                    }
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
                                .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Muhabbet.icons.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(MuhabbetSizes.IconLarge)
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
                            modifier = Modifier.fillMaxWidth().padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Muhabbet.icons.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(MuhabbetSizes.IconLarge)
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

                // Block & Report section
                item {
                    Spacer(Modifier.height(MuhabbetSpacing.Small))
                    // isBlocked == null while the check is still in flight (or failed): the safe
                    // default is offering "Block" rather than guessing "already blocked".
                    val userIsBlocked = isBlocked == true
                    val blockRowTint = if (userIsBlocked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    val blockRowLabel = if (userIsBlocked) {
                        stringResource(Res.string.profile_unblock)
                    } else {
                        stringResource(Res.string.profile_block)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (userIsBlocked) showUnblockDialog = true else showBlockDialog = true
                            }
                            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Muhabbet.icons.Block,
                            contentDescription = blockRowLabel,
                            tint = blockRowTint,
                            modifier = Modifier.size(MuhabbetSizes.IconLarge)
                        )
                        Spacer(Modifier.width(MuhabbetSpacing.Medium))
                        Text(
                            text = blockRowLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            color = blockRowTint
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showReportDialog = true }
                            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Muhabbet.icons.Report,
                            contentDescription = stringResource(Res.string.profile_report),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(MuhabbetSizes.IconLarge)
                        )
                        Spacer(Modifier.width(MuhabbetSpacing.Medium))
                        Text(
                            text = stringResource(Res.string.profile_report),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    HorizontalDivider()
                }

                // Bottom spacing
                item { Spacer(Modifier.height(MuhabbetSpacing.XLarge)) }
            }
        }
    }
}

/**
 * One of the icon-over-label buttons under the avatar.
 *
 * `sizeIn`, not `size` (#701). `MuhabbetSizes.MinTouchTarget` is a **floor** — WCAG 2.5.5 says a
 * target may not be smaller than this, not that it must be exactly this. Pinned to it as a fixed
 * square, the 28.dp icon plus a 4.dp gap plus a label line came to roughly 50.dp of content inside
 * a 40.dp box once padding was taken off: the label overlapped the icon and was clipped at both
 * ends. Turkish and English labels differ in width too, so the width has to follow the text.
 *
 * Through `pressable` rather than a hand-rolled `clip`/`clickable` pair, so the press ripple follows
 * the rounded shape instead of the node's bounding box (#703). This was the one call site that fix
 * had to skip: clipping a box whose content already overflowed it would have cropped the label
 * rather than rounding the ripple. The `sizeIn` above is what made it safe.
 */
@Composable
private fun ProfileActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pressable(MaterialTheme.shapes.medium, onClick = onClick)
            .sizeIn(
                minWidth = MuhabbetSizes.MinTouchTarget,
                minHeight = MuhabbetSizes.MinTouchTarget
            )
            .padding(horizontal = MuhabbetSpacing.Medium, vertical = MuhabbetSpacing.Small)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(MuhabbetSizes.IconLarge)
        )
        Spacer(Modifier.height(MuhabbetSpacing.XSmall))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
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
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUrl = group.avatarUrl,
            displayName = group.name,
            size = MuhabbetSizes.AvatarSmall,
            isGroup = true,
            contentDescription = stringResource(Res.string.cd_group_avatar)
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
                text = pluralStringResource(Res.plurals.group_participant_count, group.memberCount, group.memberCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val TAG = "UserProfileScreen"
