package com.muhabbet.app.ui.group

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.ui.people.PeoplePicker
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.components.MuhabbetBottomSheet
import com.muhabbet.designsystem.components.MuhabbetButton
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.ParticipantResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Adding people to a group that already exists.
 *
 * There was no way to do this at all before #520. `GroupInfoScreen` could remove a member and share
 * an invite link, but nothing added one — the `group_add_members` string had been sitting in both
 * locales, referenced by no Kotlin file, since the screen was written. A group therefore had exactly
 * the membership it was created with, and since creating one was itself gated on the address book,
 * an account that declined contacts had no route to a group of any shape.
 *
 * The candidate list is [PeoplePicker], so this inherits the same three ways in and the same
 * refusal to demand a permission for data it already holds.
 *
 * **Reports inline rather than through a snackbar**, for the reason spelled out in
 * `PersonByNumberSheet`: a snackbar raised from inside a `ModalBottomSheet` renders behind the
 * sheet's own scrim. Success needs no report — the member list behind the sheet grows.
 *
 * @param existingMemberIds people already in the group. Passed to the picker as an exclusion rather
 *   than filtered afterwards, so an existing member cannot be offered by any of its three sources —
 *   including a by-number lookup, which no post-filter of the visible list would have caught.
 * @param onMembersAdded the members the server accepted, so the caller can grow its list without a
 *   refetch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupMembersSheet(
    conversationId: String,
    existingMemberIds: Set<String>,
    onDismiss: () -> Unit,
    onMembersAdded: (List<ParticipantResponse>) -> Unit,
    groupRepository: GroupRepository = koinInject()
) {
    var selectedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isAdding by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MuhabbetBottomSheet(
        onDismiss = onDismiss,
        contentPadding = PaddingValues(
            horizontal = MuhabbetSpacing.XLarge,
            vertical = MuhabbetSpacing.Medium
        )
    ) {
        Text(
            text = stringResource(Res.string.group_add_members),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(MuhabbetSpacing.Medium))

        PeoplePicker(
            selectedUserIds = selectedUserIds,
            onSelectionChanged = { userId, selected ->
                selectedUserIds = if (selected) selectedUserIds + userId else selectedUserIds - userId
                failed = false
            },
            emptyLabel = stringResource(Res.string.people_picker_empty_add_members),
            excludeUserIds = existingMemberIds,
            // Capped: an uncapped LazyColumn in a sheet grows to full height and pushes the confirm
            // button below the fold, where it cannot be reached.
            listMaxHeight = MuhabbetSizes.PickerSheetMaxHeight
        )

        Spacer(Modifier.height(MuhabbetSpacing.Large))

        MuhabbetButton(
            text = if (isAdding) {
                stringResource(Res.string.group_add_members_adding)
            } else {
                stringResource(Res.string.group_add_members_confirm)
            },
            onClick = addMembers@{
                if (isAdding || selectedUserIds.isEmpty()) return@addMembers
                failed = false
                isAdding = true
                scope.launch {
                    val result = runCatchingCancellable {
                        groupRepository.addMembers(conversationId, selectedUserIds.toList())
                    }
                    isAdding = false
                    result
                        .onSuccess { added ->
                            onMembersAdded(added)
                            onDismiss()
                        }
                        .onFailure { e ->
                            Log.e(TAG, "Could not add members to $conversationId", e)
                            failed = true
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            role = MuhabbetButtonRole.Primary,
            enabled = selectedUserIds.isNotEmpty() && !isAdding
        )

        if (failed) {
            Spacer(Modifier.height(MuhabbetSpacing.Large))
            Text(
                text = stringResource(Res.string.group_add_members_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(MuhabbetSpacing.XLarge))
    }
}

private const val TAG = "AddGroupMembersSheet"
