package com.muhabbet.app.ui.call

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.muhabbet.composeapp.generated.resources.call_coming_soon
import com.muhabbet.composeapp.generated.resources.call_coming_soon_detail
import com.muhabbet.composeapp.generated.resources.call_incoming
import com.muhabbet.composeapp.generated.resources.call_missed
import com.muhabbet.composeapp.generated.resources.call_outgoing
import com.muhabbet.composeapp.generated.resources.call_video
import com.muhabbet.composeapp.generated.resources.call_voice
import com.muhabbet.composeapp.generated.resources.calls_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetScaffold
import com.muhabbet.designsystem.components.MuhabbetLoadingState
import com.muhabbet.designsystem.components.MuhabbetEmptyState
import com.muhabbet.designsystem.components.MuhabbetIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    showTopBar: Boolean = true
) {
    val callRepository = koinInject<CallRepository>()
    val tokenStorage = koinInject<TokenStorage>()
    val currentUserId = tokenStorage.getUserId()

    var calls by remember { mutableStateOf<List<CallHistoryResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val errorLoadMsg = stringResource(Res.string.error_load_failed)
    val title = stringResource(Res.string.calls_title)
    val voiceLabel = stringResource(Res.string.call_voice)
    val videoLabel = stringResource(Res.string.call_video)
    val incomingLabel = stringResource(Res.string.call_incoming)
    val outgoingLabel = stringResource(Res.string.call_outgoing)
    val missedLabel = stringResource(Res.string.call_missed)
    // Calling has never worked (#367–#373): the client never sends call.initiate, no mic track is
    // ever published, and LiveKit is unconfigured in prod. Tapping a row or the call-back icon used
    // to mint a fake call id and push ActiveCallScreen, which then sat on "Connecting…" forever
    // because no call.room frame was ever coming. This is the same "coming soon" message
    // ProfileScreen's call button already shows, so the two honest surfaces agree.
    val callComingSoonMsg = stringResource(Res.string.call_coming_soon)
    val callComingSoonDetail = stringResource(Res.string.call_coming_soon_detail)
    val onCallUser: (userId: String, name: String?, callType: String) -> Unit = { _, _, _ ->
        scope.launch { snackbarHostState.showSnackbar(callComingSoonMsg) }
    }

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
            // Without this a failed load is indistinguishable from the empty state below, which
            // explains why there are no calls for a reason that is not "the request failed".
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
        // No FAB (#684). The Calls tab used to carry a phone FAB that opened NewConversationScreen
        // in call-picker mode — a screen whose title still read "New Conversation", and which asks
        // for the contacts permission before it can show anything. That is a real permission
        // prompt bought for a flow that cannot end in a call: nothing sends call.initiate
        // (#367–#373). The empty state below already says calling is not available yet, and a
        // bright button underneath that sentence contradicts it.
        //
        // Config.PickContactForCall and NewConversationScreen's isCallPickerMode are deliberately
        // left in place — they are the right destination the day calling is wired. Only the FAB
        // that reaches them today is gone.
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (isLoading) {
            MuhabbetLoadingState(Modifier.fillMaxSize().padding(padding))
        } else if (calls.isEmpty()) {
            // This is the state every user sees, always: `call_history` has 0 rows in production
            // and cannot gain one, because nothing ever sends `call.initiate` (#367). A bare
            // "Henüz arama yok" therefore said something true about the table and something false
            // about the app — it reads as "you have not called anyone yet", which invites the user
            // to go and try. The headline now says what is actually the case, and the disclosure
            // lands before any tap rather than in a snackbar after two.
            //
            // `call_history_empty` is deliberately left declared in both locales: it is the right
            // string the day a real call can be missing from a real list.
            MuhabbetEmptyState(
                modifier = Modifier.padding(padding),
                icon = Muhabbet.icons.CallStart,
                title = callComingSoonMsg,
                subtitle = callComingSoonDetail
            )
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
