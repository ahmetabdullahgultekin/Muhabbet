package com.muhabbet.app.ui.group

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
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
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.dto.ParticipantResponse
import com.muhabbet.shared.model.MemberRole
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    conversationId: String,
    conversationName: String,
    onBack: () -> Unit,
    conversationRepository: ConversationRepository = koinInject(),
    groupRepository: GroupRepository = koinInject(),
    tokenStorage: TokenStorage = koinInject()
) {
    var conversation by remember { mutableStateOf<ConversationResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentUserId = remember { tokenStorage.getUserId() ?: "" }
    val updateFailedMsg = stringResource(Res.string.group_update_failed)
    val leaveFailedMsg = stringResource(Res.string.group_leave_failed)
    val removeFailedMsg = stringResource(Res.string.group_remove_failed)

    LaunchedEffect(conversationId) {
        try {
            val convs = conversationRepository.getConversations()
            conversation = convs.items.firstOrNull { it.id == conversationId }
        } catch (_: Exception) { }
        isLoading = false
    }

    val myRole = conversation?.participants
        ?.firstOrNull { it.userId == currentUserId }
        ?.role ?: MemberRole.MEMBER
    val isAdminOrOwner = myRole == MemberRole.OWNER || myRole == MemberRole.ADMIN

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(Res.string.group_edit_name_title)) },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    placeholder = { Text(stringResource(Res.string.group_edit_name_placeholder)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editName.isNotBlank()) {
                        scope.launch {
                            try {
                                groupRepository.updateGroupInfo(conversationId, editName, null)
                                conversation = conversation?.copy(name = editName)
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar(updateFailedMsg)
                            }
                        }
                    }
                    showEditDialog = false
                }) { Text(stringResource(Res.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text(stringResource(Res.string.group_leave_title)) },
            text = { Text(stringResource(Res.string.group_leave_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            groupRepository.leaveGroup(conversationId)
                            onBack()
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(leaveFailedMsg)
                        }
                    }
                    showLeaveDialog = false
                }) { Text(stringResource(Res.string.group_leave_button), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) { Text(stringResource(Res.string.cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.group_info_title)) },

                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (isAdminOrOwner) {
                        IconButton(onClick = {
                            editName = conversation?.name ?: ""
                            showEditDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    }
                    IconButton(onClick = { showLeaveDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                // Group name header
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(72.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = com.muhabbet.app.ui.profile.firstGrapheme(conversation?.name ?: "G"),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = conversation?.name ?: conversationName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(Res.string.group_participant_count, conversation?.participants?.size ?: 0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                }

                // Members header
                item {
                    Text(
                        text = stringResource(Res.string.group_members),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                // Member list
                val members = conversation?.participants ?: emptyList()
                items(members, key = { it.userId }) { member ->
                    MemberItem(
                        member = member,
                        isCurrentUser = member.userId == currentUserId,
                        canRemove = isAdminOrOwner && member.userId != currentUserId && member.role != MemberRole.OWNER,
                        onRemove = {
                            scope.launch {
                                try {
                                    groupRepository.removeMember(conversationId, member.userId)
                                    conversation = conversation?.copy(
                                        participants = conversation!!.participants.filter { it.userId != member.userId }
                                    )
                                } catch (_: Exception) {
                                    snackbarHostState.showSnackbar(removeFailedMsg)
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MemberItem(
    member: ParticipantResponse,
    isCurrentUser: Boolean,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp).clip(CircleShape),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = com.muhabbet.app.ui.profile.firstGrapheme(member.displayName ?: "?"),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = (member.displayName ?: member.phoneNumber ?: stringResource(Res.string.unknown)) +
                            if (isCurrentUser) " " + stringResource(Res.string.group_member_you) else "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (member.role == MemberRole.OWNER) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            stringResource(Res.string.group_role_owner),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (member.role == MemberRole.ADMIN) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            stringResource(Res.string.group_role_admin),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (member.phoneNumber != null) {
                Text(
                    text = member.phoneNumber!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (canRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
