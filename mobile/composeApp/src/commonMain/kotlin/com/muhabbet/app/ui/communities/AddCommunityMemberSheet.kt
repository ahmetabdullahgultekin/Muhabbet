package com.muhabbet.app.ui.communities

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.components.MuhabbetBottomSheet
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.shared.dto.CommunityMemberCandidateResponse
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Member picker for a community.
 *
 * The candidate list comes from the server, not from the phone's contacts, because since #375 the
 * only people who can be enrolled are those already in one of the community's own groups. A picker
 * built from contacts would offer names that every add then rejects with
 * `COMMUNITY_MEMBER_NOT_IN_ANY_GROUP` — a button that always fails is worse than no button.
 *
 * No filtering happens here: the endpoint already excludes existing members, so the sheet shows
 * exactly what the add will accept.
 *
 * @param onMemberAdded invoked after a successful add so the caller can refresh its list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCommunityMemberSheet(
    communityId: String,
    onDismiss: () -> Unit,
    onMemberAdded: () -> Unit,
    snackbarHostState: SnackbarHostState,
    communityRepository: CommunityRepository = koinInject()
) {
    val scope = rememberCoroutineScope()

    var candidates by remember { mutableStateOf<List<CommunityMemberCandidateResponse>?>(null) }
    var isAdding by remember { mutableStateOf(false) }

    val addedMsg = stringResource(Res.string.community_add_member_added)
    val failedMsg = stringResource(Res.string.community_add_member_failed)
    val loadFailedMsg = stringResource(Res.string.error_load_failed)

    LaunchedEffect(communityId) {
        runCatchingCancellable {
            candidates = communityRepository.getAddableUsers(communityId)
        }.onFailure { e ->
            // An empty list and a failed request must not look the same: "nobody is eligible" is a
            // real answer here, so a swallowed error would read as one.
            Log.e(TAG, "Failed to load addable users for community $communityId", e)
            candidates = emptyList()
            snackbarHostState.showSnackbar(loadFailedMsg)
        }
    }

    MuhabbetBottomSheet(
        onDismiss = onDismiss,
        contentPadding = PaddingValues(
            horizontal = MuhabbetSpacing.XLarge,
            vertical = MuhabbetSpacing.Medium
        )
    ) {
        Column {
            Text(
                text = stringResource(Res.string.community_add_member_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(MuhabbetSpacing.XSmall))
            Text(
                text = stringResource(Res.string.community_add_member_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(MuhabbetSpacing.Large))

            val people = candidates
            when {
                people == null -> MuhabbetLoadingState(
                    Modifier.fillMaxWidth().padding(MuhabbetSpacing.XLarge)
                )

                people.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(MuhabbetSpacing.XLarge),
                    contentAlignment = Alignment.Center
                ) {
                    // Says why rather than just "empty": with no invite flow (#387) this is the
                    // normal state of a community whose groups everyone already belongs to.
                    Text(
                        text = stringResource(Res.string.community_add_member_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> LazyColumn(modifier = Modifier.heightIn(max = MuhabbetSizes.PickerSheetMaxHeight)) {
                    items(people, key = { it.userId }) { candidate ->
                        CandidateItem(
                            candidate = candidate,
                            enabled = !isAdding,
                            onClick = {
                                if (isAdding) return@CandidateItem
                                scope.launch {
                                    isAdding = true
                                    var addFailed = false
                                    try {
                                        communityRepository.addMemberToCommunity(communityId, candidate.userId)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to add ${candidate.userId} to $communityId", e)
                                        addFailed = true
                                    }
                                    // Clear the spinner BEFORE reporting — showSnackbar suspends
                                    // until dismissed (~4s).
                                    isAdding = false
                                    if (addFailed) {
                                        snackbarHostState.showSnackbar(failedMsg)
                                    } else {
                                        // The report precedes the dismiss on purpose: this
                                        // coroutine runs on the sheet's own rememberCoroutineScope,
                                        // so onDismiss() takes the sheet out of composition and
                                        // cancels it — a snackbar started after that never appears.
                                        snackbarHostState.showSnackbar(addedMsg)
                                        onMemberAdded()
                                        onDismiss()
                                    }
                                }
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(MuhabbetSpacing.Large))
        }
    }
}

@Composable
private fun CandidateItem(
    candidate: CommunityMemberCandidateResponse,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val displayName = candidate.displayName ?: stringResource(Res.string.unknown)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            avatarUrl = candidate.avatarUrl,
            displayName = displayName,
            size = MuhabbetSizes.AvatarMedium
        )
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Text(
            text = displayName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
    }
}

private const val TAG = "AddCommunityMemberSheet"
