package com.muhabbet.app.ui.conversations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.BroadcastListRepository
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.components.UserAvatar
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.action_retry
import com.muhabbet.composeapp.generated.resources.action_back
import com.muhabbet.composeapp.generated.resources.broadcast_detail_no_recipients
import com.muhabbet.composeapp.generated.resources.broadcast_detail_recipients
import com.muhabbet.composeapp.generated.resources.broadcast_detail_title
import com.muhabbet.composeapp.generated.resources.unknown
import com.muhabbet.composeapp.generated.resources.error_generic
import com.muhabbet.shared.dto.BroadcastMemberResponse
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetErrorState

private const val TAG = "BroadcastDetailScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BroadcastDetailScreen(
    broadcastListId: String,
    broadcastListName: String,
    onBack: () -> Unit,
    broadcastListRepository: BroadcastListRepository = koinInject()
) {
    // The shared DTO, not a private copy. The local one this replaces declared the two fields the
    // old controller happened to emit, which is how a screen can compile against a shape the
    // server does not serve.
    var members by remember { mutableStateOf<List<BroadcastMemberResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    // Bumped by the error state's retry button. Keying the effect on it re-runs the load without
    // needing a coroutine scope at the call site.
    var retryKey by remember { mutableStateOf(0) }

    LaunchedEffect(broadcastListId, retryKey) {
        isLoading = true
        loadError = false
        runCatchingCancellable {
            members = broadcastListRepository.getBroadcastListMembers(broadcastListId)
        }.onFailure { e ->
            Log.e(TAG, "Failed to load recipients of $broadcastListId", e)
            loadError = true
        }
        isLoading = false
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = broadcastListName.ifBlank { stringResource(Res.string.broadcast_detail_title) },
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        }
    ) { padding ->
        when {
            isLoading -> MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
            loadError -> MuhabbetErrorState(
                message = stringResource(Res.string.error_generic),
                modifier = Modifier.fillMaxSize().padding(padding),
                retryLabel = stringResource(Res.string.action_retry),
                onRetry = { retryKey++ }
            )
            members.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Muhabbet.icons.Channel,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.padding(top = MuhabbetSpacing.Medium))
                    Text(
                        text = stringResource(Res.string.broadcast_detail_no_recipients),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                item {
                    Text(
                        text = "${stringResource(Res.string.broadcast_detail_recipients)} (${members.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(
                            horizontal = MuhabbetSpacing.XLarge,
                            vertical = MuhabbetSpacing.Medium
                        )
                    )
                    HorizontalDivider()
                }
                itemsIndexed(members) { _, member ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = MuhabbetElevation.Level1
                    ) {
                        // Was `member.userId` — a column of raw UUIDs. The endpoint had never been
                        // reached, so nobody had seen what it renders.
                        val displayName = member.displayName ?: stringResource(Res.string.unknown)
                        Row(
                            modifier = Modifier.padding(
                                horizontal = MuhabbetSpacing.Large,
                                vertical = MuhabbetSpacing.Medium
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            UserAvatar(
                                avatarUrl = member.avatarUrl,
                                displayName = displayName,
                                size = MuhabbetSizes.AvatarSmall
                            )
                            Spacer(Modifier.width(MuhabbetSpacing.Medium))
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
