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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.app.platform.CallEngine
import com.muhabbet.shared.model.CallEndReason
import com.muhabbet.shared.model.CallType
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.muhabbet.app.util.Log
import com.muhabbet.app.util.runCatchingCancellable
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.call_connected
import com.muhabbet.composeapp.generated.resources.call_connecting
import com.muhabbet.composeapp.generated.resources.call_connection_failed
import com.muhabbet.composeapp.generated.resources.call_end
import com.muhabbet.composeapp.generated.resources.call_mute
import com.muhabbet.composeapp.generated.resources.call_speaker
import com.muhabbet.composeapp.generated.resources.call_unmute
import com.muhabbet.composeapp.generated.resources.call_video
import com.muhabbet.composeapp.generated.resources.call_voice
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet

/**
 * Where the call actually is, as opposed to where the screen happens to be.
 *
 * The screen opens on [CONNECTING] and stays there while the call rings: media only comes up once a
 * `call.room` frame arrives with LiveKit credentials. A two-state boolean could not tell "ringing"
 * from "up", so the screen used to claim "Connected" the instant it opened.
 */
private enum class CallMediaState { CONNECTING, CONNECTED, FAILED }

@Composable
fun ActiveCallScreen(
    callId: String,
    otherUserId: String,
    otherUserName: String?,
    callType: CallType,
    onCallEnded: () -> Unit
) {
    val wsClient = koinInject<WsClient>()
    val scope = rememberCoroutineScope()
    val callEngine = remember { CallEngine() }

    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var callDurationSeconds by remember(callId) { mutableStateOf(0) }
    var mediaState by remember(callId) { mutableStateOf(CallMediaState.CONNECTING) }

    val endLabel = stringResource(Res.string.call_end)
    val muteLabel = stringResource(Res.string.call_mute)
    val unmuteLabel = stringResource(Res.string.call_unmute)
    val speakerLabel = stringResource(Res.string.call_speaker)
    val connectingLabel = stringResource(Res.string.call_connecting)
    val connectedLabel = stringResource(Res.string.call_connected)
    val connectFailedLabel = stringResource(Res.string.call_connection_failed)
    val callTypeLabel = if (callType == CallType.VIDEO)
        stringResource(Res.string.call_video)
    else
        stringResource(Res.string.call_voice)

    // Duration timer — keyed on the media state, not just callId, so it only counts once media is
    // actually up. It used to start at screen open, which meant a call that was still ringing (or
    // that never connected at all) still showed a duration ticking away underneath.
    LaunchedEffect(callId, mediaState) {
        if (mediaState != CallMediaState.CONNECTED) return@LaunchedEffect
        while (true) {
            delay(com.muhabbet.designsystem.theme.MuhabbetDurations.CallTimerTickMs)
            callDurationSeconds++
        }
    }

    // Listen for call.room (LiveKit credentials) and call.end from other party
    LaunchedEffect(callId) {
        wsClient.incoming.collect { message ->
            when (message) {
                is WsMessage.CallRoomInfo -> {
                    if (message.callId == callId && message.serverUrl.isNotBlank()) {
                        // runCatchingCancellable, not try/catch: connect() is suspend, so hanging up
                        // mid-connect cancels it — and a plain catch would read that cancellation as
                        // a media failure and flip the banner on the way out of the call.
                        runCatchingCancellable { callEngine.connect(message.serverUrl, message.token) }
                            .onSuccess { mediaState = CallMediaState.CONNECTED }
                            .onFailure { e ->
                                // Without this the screen keeps counting up as if the call were live
                                // while no audio flows at all. Surfaced as a status line below.
                                Log.e("ActiveCallScreen", "Failed to connect call media", e)
                                mediaState = CallMediaState.FAILED
                            }
                    }
                }
                is WsMessage.CallEnd -> {
                    if (message.callId == callId) {
                        callEngine.disconnect()
                        onCallEnded()
                    }
                }
                else -> { }
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            callEngine.disconnect()
        }
    }

    val minutes = callDurationSeconds / 60
    val seconds = callDurationSeconds % 60

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            // Insets go on the content, not the Box: the background stays full-bleed while the
            // call controls stay clear of the gesture bar. There is no Scaffold on this screen.
            modifier = Modifier.safeDrawingPadding().padding(MuhabbetSpacing.XXLarge)
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (otherUserName ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(MuhabbetSpacing.XLarge))

            Text(
                text = otherUserName ?: otherUserId,
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

            // Duration — withheld entirely while the call is still ringing. A clock under a
            // "Connecting…" line would read as call duration, which is exactly the false claim
            // the tri-state above exists to remove.
            if (mediaState != CallMediaState.CONNECTING) {
                Text(
                    text = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Media status — the timer alone cannot tell "connected" from "no audio at all".
            Text(
                text = when (mediaState) {
                    CallMediaState.CONNECTING -> connectingLabel
                    CallMediaState.CONNECTED -> connectedLabel
                    CallMediaState.FAILED -> connectFailedLabel
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (mediaState == CallMediaState.FAILED) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Call controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XXLarge),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            callEngine.setMuted(isMuted)
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMuted) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = if (isMuted) Muhabbet.icons.MicOff else Muhabbet.icons.Mic,
                            contentDescription = if (isMuted) unmuteLabel else muteLabel,
                            modifier = Modifier.size(MuhabbetSizes.IconLarge)
                        )
                    }
                    Spacer(modifier = Modifier.height(MuhabbetSpacing.XSmall))
                    Text(
                        text = if (isMuted) unmuteLabel else muteLabel,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // End Call
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            callEngine.disconnect()
                            scope.launch {
                                runCatchingCancellable {
                                    wsClient.send(WsMessage.CallEnd(callId = callId, reason = CallEndReason.ENDED))
                                }.onFailure { e ->
                                    // Deliberately silent in the UI: hanging up must always succeed
                                    // locally and this screen is already gone by now. The peer falls
                                    // back to its own call timeout.
                                    Log.e("ActiveCallScreen", "Failed to notify peer of call end", e)
                                }
                            }
                            onCallEnded()
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(LocalSemanticColors.current.callDecline)
                    ) {
                        Icon(
                            imageVector = Muhabbet.icons.CallEnd,
                            contentDescription = endLabel,
                            tint = LocalSemanticColors.current.onCallDecline,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(MuhabbetSpacing.XSmall))
                    Text(text = endLabel, style = MaterialTheme.typography.labelSmall)
                }

                // Speaker
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            isSpeaker = !isSpeaker
                            callEngine.setSpeaker(isSpeaker)
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSpeaker) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    ) {
                        Icon(
                            imageVector = Muhabbet.icons.Speaker,
                            contentDescription = speakerLabel,
                            modifier = Modifier.size(MuhabbetSizes.IconLarge)
                        )
                    }
                    Spacer(modifier = Modifier.height(MuhabbetSpacing.XSmall))
                    Text(text = speakerLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
