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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.platform.CallEngine
import com.muhabbet.shared.model.CallEndReason
import com.muhabbet.shared.model.CallType
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import muhabbet.mobile.composeapp.generated.resources.Res
import muhabbet.mobile.composeapp.generated.resources.call_connected
import muhabbet.mobile.composeapp.generated.resources.call_end
import muhabbet.mobile.composeapp.generated.resources.call_mute
import muhabbet.mobile.composeapp.generated.resources.call_speaker
import muhabbet.mobile.composeapp.generated.resources.call_unmute
import muhabbet.mobile.composeapp.generated.resources.call_video
import muhabbet.mobile.composeapp.generated.resources.call_voice
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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
    var callDurationSeconds by remember { mutableStateOf(0) }

    val endLabel = stringResource(Res.string.call_end)
    val muteLabel = stringResource(Res.string.call_mute)
    val unmuteLabel = stringResource(Res.string.call_unmute)
    val speakerLabel = stringResource(Res.string.call_speaker)
    val connectedLabel = stringResource(Res.string.call_connected)
    val callTypeLabel = if (callType == CallType.VIDEO)
        stringResource(Res.string.call_video)
    else
        stringResource(Res.string.call_voice)

    // Duration timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callDurationSeconds++
        }
    }

    // Listen for call.room (LiveKit credentials) and call.end from other party
    LaunchedEffect(callId) {
        wsClient.incoming.collect { message ->
            when (message) {
                is WsMessage.CallRoomInfo -> {
                    if (message.callId == callId && message.serverUrl.isNotBlank()) {
                        try {
                            callEngine.connect(message.serverUrl, message.token)
                        } catch (_: Exception) { }
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
            modifier = Modifier.padding(32.dp)
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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = otherUserName ?: otherUserId,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = callTypeLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Duration
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Call controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
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
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) unmuteLabel else muteLabel,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
                                try {
                                    wsClient.send(WsMessage.CallEnd(callId = callId, reason = CallEndReason.ENDED))
                                } catch (_: Exception) { }
                            }
                            onCallEnded()
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = endLabel,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = speakerLabel,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = speakerLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
