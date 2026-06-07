package com.muhabbet.app.ui.communities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.app.data.repository.ConversationRepository
import com.muhabbet.app.ui.components.UserAvatar
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.ConversationResponse
import com.muhabbet.shared.model.ConversationType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Group-picker bottom sheet for adding an existing group to a community.
 *
 * Lists the user's groups that are not already part of the community, and on
 * selection calls the existing [CommunityRepository.addGroupToCommunity] endpoint.
 * No business logic lives here: eligible-group filtering is a trivial UI concern
 * (group type, exclude already-joined) and the add/refresh go through repositories.
 *
 * @param excludeConversationIds conversation ids already in the community, hidden from the picker.
 * @param onGroupAdded invoked after a successful add so the caller can refresh the detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupToCommunitySheet(
    communityId: String,
    excludeConversationIds: Set<String>,
    onDismiss: () -> Unit,
    onGroupAdded: () -> Unit,
    snackbarHostState: SnackbarHostState,
    communityRepository: CommunityRepository = koinInject(),
    conversationRepository: ConversationRepository = koinInject()
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    var eligibleGroups by remember { mutableStateOf<List<ConversationResponse>?>(null) }
    var isAdding by remember { mutableStateOf(false) }

    val addedMsg = stringResource(Res.string.community_add_group_added)
    val failedMsg = stringResource(Res.string.community_add_group_failed)

    LaunchedEffect(communityId, excludeConversationIds) {
        eligibleGroups = try {
            conversationRepository.getConversations(limit = 100).items
                .filter { it.type == ConversationType.GROUP && it.id !in excludeConversationIds }
        } catch (_: Exception) {
            emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = MuhabbetSpacing.XLarge,
                vertical = MuhabbetSpacing.Medium
            )
        ) {
            Text(
                text = stringResource(Res.string.community_add_group_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(MuhabbetSpacing.XSmall))
            Text(
                text = stringResource(Res.string.community_add_group_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(MuhabbetSpacing.Large))

            val groups = eligibleGroups
            when {
                groups == null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(MuhabbetSpacing.XLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }

                groups.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(MuhabbetSpacing.XLarge),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.community_add_group_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(groups, key = { it.id }) { group ->
                            GroupPickerItem(
                                group = group,
                                enabled = !isAdding,
                                onClick = {
                                    if (isAdding) return@GroupPickerItem
                                    scope.launch {
                                        isAdding = true
                                        try {
                                            communityRepository.addGroupToCommunity(communityId, group.id)
                                            snackbarHostState.showSnackbar(addedMsg)
                                            onGroupAdded()
                                            onDismiss()
                                        } catch (_: Exception) {
                                            snackbarHostState.showSnackbar(failedMsg)
                                        }
                                        isAdding = false
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(MuhabbetSpacing.Large))
        }
    }
}

@Composable
private fun GroupPickerItem(
    group: ConversationResponse,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUrl = group.avatarUrl,
            displayName = group.name ?: "",
            size = 44.dp
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Text(
            text = group.name ?: "",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}
