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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.muhabbet.app.data.remote.WsClient
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.app.ui.theme.LocalSemanticColors
import com.muhabbet.shared.model.CallEndReason
import com.muhabbet.shared.model.CallType
import com.muhabbet.shared.protocol.WsMessage
import kotlinx.coroutines.launch
import muhabbet.mobile.composeapp.generated.resources.Res
import muhabbet.mobile.composeapp.generated.resources.call_accept
import muhabbet.mobile.composeapp.generated.resources.call_decline
import muhabbet.mobile.composeapp.generated.resources.call_ringing
import muhabbet.mobile.composeapp.generated.resources.call_video
import muhabbet.mobile.composeapp.generated.resources.call_voice
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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
    val ringingLabel = stringResource(Res.string.call_ringing)
    val callTypeLabel = if (callType == CallType.VIDEO)
        stringResource(Res.string.call_video)
    else
        stringResource(Res.string.call_voice)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(MuhabbetSpacing.XXLarge)
        ) {
            // Caller avatar placeholder
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (callerName ?: "?").take(1).uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

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

            Spacer(modifier = Modifier.height(64.dp))

            // Accept / Decline buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Decline
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    wsClient.send(WsMessage.CallAnswer(callId = callId, accepted = false))
                                } catch (_: Exception) { }
                            }
                            onDecline()
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(LocalSemanticColors.current.callDecline)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = declineLabel,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
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
                                try {
                                    wsClient.send(WsMessage.CallAnswer(callId = callId, accepted = true))
                                } catch (_: Exception) { }
                            }
                            onAccept()
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(LocalSemanticColors.current.callAccept)
                    ) {
                        Icon(
                            imageVector = if (callType == CallType.VIDEO) Icons.Default.Videocam else Icons.Default.Call,
                            contentDescription = acceptLabel,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(MuhabbetSpacing.Small))
                    Text(text = acceptLabel, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
