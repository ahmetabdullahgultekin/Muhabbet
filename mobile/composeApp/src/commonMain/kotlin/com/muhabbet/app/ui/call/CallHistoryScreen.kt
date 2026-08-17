package com.muhabbet.app.ui.call

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.local.TokenStorage
import com.muhabbet.designsystem.components.MuhabbetTopBar
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.app.data.repository.CallRepository
import com.muhabbet.shared.dto.CallHistoryResponse
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.error_load_failed
import com.muhabbet.composeapp.generated.resources.action_back
import com.muhabbet.composeapp.generated.resources.call_history_empty
import com.muhabbet.composeapp.generated.resources.calls_new_call
import com.muhabbet.composeapp.generated.resources.call_incoming
import com.muhabbet.composeapp.generated.resources.call_missed
import com.muhabbet.composeapp.generated.resources.call_outgoing
import com.muhabbet.composeapp.generated.resources.call_video
import com.muhabbet.composeapp.generated.resources.call_voice
import com.muhabbet.composeapp.generated.resources.calls_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.components.MuhabbetFab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    onBack: () -> Unit,
    onCallUser: (userId: String, name: String?, callType: String) -> Unit,
    /**
     * Opens a contact picker so a call can be started from this tab. Without it the tab was a dead
     * end for anyone with an empty history — which is every new user — and calling was reachable
     * only from inside an existing conversation.
     */
    onNewCall: (() -> Unit)? = null,
    showBackButton: Boolean = true,
    showTopBar: Boolean = true
) {
    val callRepository = koinInject<CallRepository>()
    val tokenStorage = koinInject<TokenStorage>()
    val currentUserId = tokenStorage.getUserId()

    var calls by remember { mutableStateOf<List<CallHistoryResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }

    val errorLoadMsg = stringResource(Res.string.error_load_failed)
    val emptyText = stringResource(Res.string.call_history_empty)
    val title = stringResource(Res.string.calls_title)
    val voiceLabel = stringResource(Res.string.call_voice)
    val videoLabel = stringResource(Res.string.call_video)
    val incomingLabel = stringResource(Res.string.call_incoming)
    val outgoingLabel = stringResource(Res.string.call_outgoing)
    val missedLabel = stringResource(Res.string.call_missed)

    LaunchedEffect(Unit) {
        val failure = runCatchingCancellable {
            val result = callRepository.getCallHistory()
            calls = result.items
        }.exceptionOrNull()
        // Clear the spinner BEFORE reporting: showSnackbar suspends until the snackbar is
        // dismissed (~4s for Short), so reporting first leaves the loading indicator spinning
        // over content that has already finished loading.
        isLoading = false
        if (failure != null) {
            // Without this the screen shows the "no calls yet" empty state, which is a lie.
            Log.e("CallHistoryScreen", "Failed to load call history", failure)
            snackbarHostState.showSnackbar(errorLoadMsg)
        }
    }

    MuhabbetScaffold(
        topBar = {
            if (showTopBar) {
                // Embedded as the Calls tab, this screen has no back button; pushed as its own
                // destination, it does. Expressed as a nullable callback rather than an `if` inside
                // the navigationIcon slot, which is what left an empty slot behind before.
                MuhabbetTopBar(
                    title = title,
                    onBack = if (showBackButton) onBack else null,
                    backContentDescription = stringResource(Res.string.action_back)
                )
            }
        },
        floatingActionButton = {
            if (onNewCall != null) {
                MuhabbetFab(
                    icon = Muhabbet.icons.CallStart,
                    contentDescription = stringResource(Res.string.calls_new_call),
                    onClick = onNewCall
                )
            }
        },
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (isLoading) {
            MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
        } else if (calls.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                items(calls, key = { it.id }) { call ->
                    val isOutgoing = call.callerId == currentUserId
                    val isMissed = call.status == "MISSED"
                    val isDeclined = call.status == "DECLINED"
                    val otherName = if (isOutgoing) call.calleeName else call.callerName
                    val otherUserId = if (isOutgoing) call.calleeId else call.callerId
                    val isVideo = call.callType == "VIDEO"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCallUser(otherUserId, otherName, call.callType) }
                            .padding(horizontal = MuhabbetSpacing.Large, vertical = MuhabbetSpacing.Medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Direction icon
                        Icon(
                            imageVector = when {
                                isMissed || isDeclined -> Muhabbet.icons.CallMissed
                                isOutgoing -> Muhabbet.icons.CallOutgoing
                                else -> Muhabbet.icons.CallIncoming
                            },
                            contentDescription = when {
                                isMissed -> missedLabel
                                isOutgoing -> outgoingLabel
                                else -> incomingLabel
                            },
                            tint = if (isMissed || isDeclined) LocalSemanticColors.current.callMissed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(MuhabbetSizes.IconMedium)
                        )

                        Spacer(modifier = Modifier.width(MuhabbetSpacing.Medium))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = otherName ?: otherUserId,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isMissed) LocalSemanticColors.current.callMissed else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = buildString {
                                    append(if (isVideo) videoLabel else voiceLabel)
                                    call.durationSeconds?.let { dur ->
                                        if (dur > 0) {
                                            val m = dur / 60; val s = dur % 60
                                            append(" · ${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}")
                                        }
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Call back icon
                        MuhabbetIconButton(
                            icon = if (isVideo) Muhabbet.icons.VideoCall else Muhabbet.icons.CallStart,
                            contentDescription = if (isVideo) videoLabel else voiceLabel,
                            onClick = { onCallUser(otherUserId, otherName, call.callType) },
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
