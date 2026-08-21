package com.muhabbet.app.ui.channels

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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.repository.ChannelRepository
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.shared.dto.ChannelInfoResponse
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetSkeletonGate
import com.muhabbet.designsystem.components.MuhabbetSkeletonList
import com.muhabbet.designsystem.components.MuhabbetButtonRole
import com.muhabbet.designsystem.components.MuhabbetButton

private const val TAG = "ChannelListScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    onBack: () -> Unit,
    onChannelClick: (id: String, name: String) -> Unit,
    channelRepository: ChannelRepository = koinInject()
) {
    var channels by remember { mutableStateOf<List<ChannelInfoResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorLoadMsg = stringResource(Res.string.error_load_failed)
    val errorActionMsg = stringResource(Res.string.error_action_failed)
    val loadingLabel = stringResource(Res.string.channels_loading)

    LaunchedEffect(Unit) {
        val failure = runCatchingCancellable { channels = channelRepository.listChannels() }.exceptionOrNull()
        // Clear the spinner BEFORE reporting — showSnackbar suspends until dismissed (~4s).
        isLoading = false
        if (failure != null) {
            // Without this the screen shows the "no channels" empty state, which is a lie.
            Log.e(TAG, "Failed to load channels", failure)
            snackbarHostState.showSnackbar(errorLoadMsg)
        }
    }

    MuhabbetScaffold(
        topBar = {
            MuhabbetTopBar(
                title = stringResource(Res.string.channels_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.action_back)
            )
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        // Same avatar-plus-two-lines row as the conversation and community lists, so the same
        // skeleton serves it.
        MuhabbetSkeletonGate(
            isLoading = isLoading,
            modifier = Modifier.fillMaxSize().padding(padding),
            skeleton = { MuhabbetSkeletonList(loadingLabel = loadingLabel) }
        ) {
            if (channels.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Muhabbet.icons.Channel,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(MuhabbetSpacing.Large))
                    Text(
                        stringResource(Res.string.channels_empty),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(channels, key = { it.id }) { channel ->
                        ChannelItem(
                            channel = channel,
                            onClick = { onChannelClick(channel.id, channel.name) },
                            onSubscribe = {
                                scope.launch {
                                    runCatchingCancellable {
                                        if (channel.isSubscribed) {
                                            channelRepository.unsubscribe(channel.id)
                                        } else {
                                            channelRepository.subscribe(channel.id)
                                        }
                                        channels = channelRepository.listChannels()
                                    }.onFailure { e ->
                                        // The subscribe button never flips on failure, so without this
                                        // the tap looks like it simply did nothing.
                                        Log.e(TAG, "Failed to toggle subscription for ${channel.id}", e)
                                        snackbarHostState.showSnackbar(errorActionMsg)
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
}

@Composable
private fun ChannelItem(
    channel: ChannelInfoResponse,
    onClick: () -> Unit,
    onSubscribe: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Muhabbet.icons.Channel,
                    contentDescription = null,
                    modifier = Modifier.size(MuhabbetSizes.IconLarge),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(MuhabbetSpacing.Medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            channel.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Text(
                text = pluralStringResource(
                    Res.plurals.channels_subscriber_count,
                    channel.subscriberCount,
                    channel.subscriberCount
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(MuhabbetSpacing.Small))
        if (channel.isSubscribed) {
            MuhabbetButton(
                text = stringResource(Res.string.channels_unsubscribe),
                onClick = onSubscribe,
                role = MuhabbetButtonRole.Secondary
            )
        } else {
            MuhabbetButton(
                text = stringResource(Res.string.channels_subscribe),
                onClick = onSubscribe,
                role = MuhabbetButtonRole.Primary
            )
        }
    }
}
