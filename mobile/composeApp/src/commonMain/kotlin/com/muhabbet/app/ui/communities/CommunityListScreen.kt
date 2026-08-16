package com.muhabbet.app.ui.communities

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.CommunityRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.CommunityResponse
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetEmptyState
import com.muhabbet.designsystem.components.MuhabbetFab
import com.muhabbet.designsystem.theme.MuhabbetSizes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityListScreen(
    onCommunityClick: (String) -> Unit,
    onCreateCommunity: () -> Unit,
    communityRepository: CommunityRepository = koinInject()
) {
    var communities by remember { mutableStateOf<List<CommunityResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    val errorLoadMsg = stringResource(Res.string.error_load_failed)

    LaunchedEffect(Unit) {
        val failure = runCatchingCancellable { communities = communityRepository.getCommunities() }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        if (failure != null) {
            // Without this the screen shows the "no communities" empty state, which is a lie.
            Log.e("CommunityListScreen", "Failed to load communities", failure)
            snackbarHostState.showSnackbar(errorLoadMsg)
        }
    }

    MuhabbetScaffold(
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            MuhabbetFab(
                icon = Muhabbet.icons.Add,
                contentDescription = stringResource(Res.string.community_create),
                onClick = onCreateCommunity
            )
        }
    ) { padding ->
        if (isLoading) {
            MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
        } else if (communities.isEmpty()) {
            MuhabbetEmptyState(
                modifier = Modifier.fillMaxSize().padding(padding),
                icon = Muhabbet.icons.TabCommunities,
                title = stringResource(Res.string.communities_empty)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)
            ) {
                items(communities, key = { it.id }) { community ->
                    CommunityListItem(
                        community = community,
                        onClick = { onCommunityClick(community.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityListItem(
    community: CommunityResponse,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        tonalElevation = MuhabbetElevation.Level1
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MuhabbetSpacing.Medium,
                vertical = MuhabbetSpacing.Medium
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                avatarUrl = community.avatarUrl,
                displayName = community.name,
                size = MuhabbetSizes.AvatarChatList
            )
            Spacer(Modifier.width(MuhabbetSpacing.Medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = community.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1
                )
                Text(
                    text = stringResource(
                        Res.string.community_group_member_summary,
                        community.groupCount,
                        community.memberCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
