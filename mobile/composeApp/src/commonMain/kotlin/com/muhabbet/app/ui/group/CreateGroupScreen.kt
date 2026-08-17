package com.muhabbet.app.ui.group

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.muhabbet.app.data.repository.GroupRepository
import com.muhabbet.app.ui.people.PeoplePicker
import com.muhabbet.app.util.Log
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.components.MuhabbetExtendedFab
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetTextField
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Naming a group and choosing who is in it.
 *
 * Until #520 this screen rendered a single control when `READ_CONTACTS` was not granted — a button
 * that uploads your address book — with no list, no search and no way past it. An account with two
 * open conversations could not put either of those two people into a group. Everything to do with
 * *who can be picked* now lives in [PeoplePicker], which offers conversations, a typed number and
 * the address book in that order; this screen is left with the group's name and the create call.
 *
 * The create affordance is unconditional. It used to be hidden unless matched contacts existed, so
 * on the accounts that hit this bug the screen had no primary action at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onGroupCreated: (id: String, name: String) -> Unit,
    onBack: () -> Unit,
    groupRepository: GroupRepository = koinInject()
) {
    var selectedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var groupName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMsg = stringResource(Res.string.error_generic)
    val groupNameRequiredMsg = stringResource(Res.string.group_name_required)
    val groupSelectMinimumMsg = stringResource(Res.string.group_select_minimum)

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.group_create_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            MuhabbetExtendedFab(
                text = stringResource(Res.string.group_create_button),
                // Explicitly labelled: the guard clauses below return from this lambda, and an
                // implicit label would silently follow whatever the enclosing call is named.
                onClick = createGroup@{
                    if (isCreating) return@createGroup
                    if (groupName.isBlank()) {
                        scope.launch { snackbarHostState.showSnackbar(groupNameRequiredMsg) }
                        return@createGroup
                    }
                    if (selectedUserIds.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar(groupSelectMinimumMsg) }
                        return@createGroup
                    }
                    isCreating = true
                    scope.launch {
                        try {
                            val conv = groupRepository.createGroup(
                                name = groupName.trim(),
                                participantIds = selectedUserIds.toList()
                            )
                            onGroupCreated(conv.id, groupName.trim())
                        } catch (e: Exception) {
                            Log.e(TAG, "Group creation failed", e)
                            // Clear the spinner BEFORE reporting — showSnackbar suspends until
                            // dismissed (~4s).
                            isCreating = false
                            snackbarHostState.showSnackbar(errorMsg)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                MuhabbetTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Small),
                    label = stringResource(Res.string.group_name_label),
                    placeholder = stringResource(Res.string.group_name_placeholder),
                    singleLine = true
                )

                Text(
                    text = stringResource(Res.string.group_participants_count, selectedUserIds.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(
                        horizontal = MuhabbetSpacing.Large,
                        vertical = MuhabbetSpacing.XSmall
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                PeoplePicker(
                    selectedUserIds = selectedUserIds,
                    onSelectionChanged = { userId, selected ->
                        selectedUserIds = if (selected) {
                            selectedUserIds + userId
                        } else {
                            selectedUserIds - userId
                        }
                    },
                    emptyLabel = stringResource(Res.string.people_picker_empty_new_group),
                    modifier = Modifier.weight(1f)
                )
            }

            if (isCreating) {
                MuhabbetLoadingState()
            }
        }
    }
}

private const val TAG = "CreateGroupScreen"
