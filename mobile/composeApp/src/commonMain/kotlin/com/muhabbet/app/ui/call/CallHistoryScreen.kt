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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.muhabbet.app.ui.theme.LocalSemanticColors
import com.muhabbet.app.data.repository.CallRepository
import com.muhabbet.shared.dto.CallHistoryResponse
import muhabbet.mobile.composeapp.generated.resources.Res
import muhabbet.mobile.composeapp.generated.resources.call_history_empty
import muhabbet.mobile.composeapp.generated.resources.call_incoming
import muhabbet.mobile.composeapp.generated.resources.call_missed
import muhabbet.mobile.composeapp.generated.resources.call_outgoing
import muhabbet.mobile.composeapp.generated.resources.call_video
import muhabbet.mobile.composeapp.generated.resources.call_voice
import muhabbet.mobile.composeapp.generated.resources.calls_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    onBack: () -> Unit,
    onCallUser: (userId: String, name: String?, callType: String) -> Unit
) {
    val callRepository = koinInject<CallRepository>()
    val tokenStorage = koinInject<TokenStorage>()
    val currentUserId = tokenStorage.getUserId()

    var calls by remember { mutableStateOf<List<CallHistoryResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val emptyText = stringResource(Res.string.call_history_empty)
    val title = stringResource(Res.string.calls_title)
    val voiceLabel = stringResource(Res.string.call_voice)
    val videoLabel = stringResource(Res.string.call_video)
    val incomingLabel = stringResource(Res.string.call_incoming)
    val outgoingLabel = stringResource(Res.string.call_outgoing)
    val missedLabel = stringResource(Res.string.call_missed)

    LaunchedEffect(Unit) {
        try {
            val result = callRepository.getCallHistory()
            calls = result.items
        } catch (_: Exception) { }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("...")
            }
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
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Direction icon
                        Icon(
                            imageVector = when {
                                isMissed || isDeclined -> Icons.Default.CallMissed
                                isOutgoing -> Icons.Default.CallMade
                                else -> Icons.Default.CallReceived
                            },
                            contentDescription = when {
                                isMissed -> missedLabel
                                isOutgoing -> outgoingLabel
                                else -> incomingLabel
                            },
                            tint = if (isMissed || isDeclined) LocalSemanticColors.current.callMissed else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

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
                                        if (dur > 0) append(" · %02d:%02d".format(dur / 60, dur % 60))
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Call back icon
                        IconButton(
                            onClick = { onCallUser(otherUserId, otherName, call.callType) }
                        ) {
                            Icon(
                                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = if (isVideo) videoLabel else voiceLabel,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
