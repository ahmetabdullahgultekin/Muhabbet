package com.muhabbet.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.shared.model.CallEndReason
import com.muhabbet.shared.model.CallType
import com.muhabbet.shared.protocol.WsMessage
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import kotlinx.coroutines.launch
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.call_accept
import com.muhabbet.composeapp.generated.resources.call_decline
import com.muhabbet.composeapp.generated.resources.call_ringing
import com.muhabbet.composeapp.generated.resources.call_video
import com.muhabbet.composeapp.generated.resources.call_voice
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.theme.breathing
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetHapticIntent
import com.muhabbet.designsystem.theme.MuhabbetGradients
import com.muhabbet.designsystem.components.UserAvatar

@Composable
fun IncomingCallScreen(
    callId: String,
    callerId: String,
    callerName: String?,
    callType: CallType,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val wsClient = koinInject<WsClient>()
    val scope = rememberCoroutineScope()

    val acceptLabel = stringResource(Res.string.call_accept)
    val declineLabel = stringResource(Res.string.call_decline)
    val haptics = Muhabbet.haptics
    val ringingLabel = stringResource(Res.string.call_ringing)
    val callTypeLabel = if (callType == CallType.VIDEO)
        stringResource(Res.string.call_video)
    else
        stringResource(Res.string.call_voice)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MuhabbetGradients.brandBackdrop),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            // See ActiveCallScreen: full-bleed background, inset controls.
            modifier = Modifier.safeDrawingPadding().padding(MuhabbetSpacing.XXLarge)
        ) {
            // Caller avatar
            UserAvatar(
                avatarUrl = null,
                displayName = callerName ?: callerId,
                size = MuhabbetSizes.AvatarCall,
                modifier = Modifier.breathing()
            )

            Spacer(modifier = Modifier.height(MuhabbetSpacing.XLarge))

            Text(
                text = callerName ?: callerId,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(MuhabbetSpacing.Small))

            Text(
                text = callTypeLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(MuhabbetSpacing.XSmall))

            Text(
                text = ringingLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(MuhabbetSpacing.XXLarge))

            // Accept / Decline buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(MuhabbetSizes.CallActionGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                runCatchingCancellable {
                                    wsClient.send(WsMessage.CallAnswer(callId = callId, accepted = false))
                                }.onFailure { e ->
                                    // Deliberately silent: declining must always dismiss locally, and
                                    // onDecline() has already navigated away by the time this lands.
                                    // The caller falls back to its own ring timeout.
                                    Log.e("IncomingCallScreen", "Failed to send call decline", e)
                                }
                            }
                            haptics.perform(MuhabbetHapticIntent.CallDeclined)
                            onDecline()
                        },
                        modifier = Modifier
                            .size(MuhabbetSizes.CallActionButton)
                            .clip(CircleShape)
                            .background(LocalSemanticColors.current.callDecline)
                    ) {
                        Icon(
                            imageVector = Muhabbet.icons.CallEnd,
                            contentDescription = declineLabel,
                            tint = LocalSemanticColors.current.onCallDecline,
                            modifier = Modifier.size(MuhabbetSizes.IconHero)
                        )
                    }
                    Spacer(modifier = Modifier.height(MuhabbetSpacing.Small))
                    Text(text = declineLabel, style = MaterialTheme.typography.bodySmall)
                }

                // Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                runCatchingCancellable {
                                    wsClient.send(WsMessage.CallAnswer(callId = callId, accepted = true))
                                }.onFailure { e ->
                                    // onAccept() has already navigated to ActiveCallScreen, so there
                                    // is nothing left here to show a message on. The failure becomes
                                    // visible there: no CallRoomInfo arrives, so no media connects.
                                    Log.e("IncomingCallScreen", "Failed to send call accept", e)
                                }
                            }
                            haptics.perform(MuhabbetHapticIntent.CallAccepted)
                            onAccept()
                        },
                        modifier = Modifier
                            .size(MuhabbetSizes.CallActionButton)
                            .clip(CircleShape)
                            .background(LocalSemanticColors.current.callAccept)
                    ) {
                        Icon(
                            imageVector = if (callType == CallType.VIDEO) Muhabbet.icons.VideoCall else Muhabbet.icons.CallStart,
                            contentDescription = acceptLabel,
                            tint = LocalSemanticColors.current.onCallAccept,
                            modifier = Modifier.size(MuhabbetSizes.IconHero)
                        )
                    }
                    Spacer(modifier = Modifier.height(MuhabbetSpacing.Small))
                    Text(text = acceptLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
