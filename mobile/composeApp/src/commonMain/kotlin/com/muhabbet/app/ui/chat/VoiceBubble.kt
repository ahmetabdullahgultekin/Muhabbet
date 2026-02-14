package com.muhabbet.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muhabbet.app.platform.AudioPlayer
import com.muhabbet.app.platform.SpeechTranscriber
import com.muhabbet.app.ui.theme.MuhabbetSpacing
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun VoiceBubble(
    mediaUrl: String,
    durationSeconds: Int?,
    isOwn: Boolean,
    audioPlayer: AudioPlayer,
    modifier: Modifier = Modifier,
    speechTranscriber: SpeechTranscriber = koinInject()
) {
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val currentPosition by audioPlayer.currentPositionMs.collectAsState()
    val duration by audioPlayer.durationMs.collectAsState()
    val scope = rememberCoroutineScope()

    var transcript by remember { mutableStateOf<String?>(null) }
    var isTranscribing by remember { mutableStateOf(false) }
    var showTranscript by remember { mutableStateOf(false) }

    val transcribeText = stringResource(Res.string.voice_transcribe)
    val transcribingText = stringResource(Res.string.voice_transcribing)
    val transcriptFailedText = stringResource(Res.string.voice_transcript_failed)

    val totalDuration = durationSeconds?.let { it * 1000L } ?: duration
    val progress = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f

    val textColor = if (isOwn) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) audioPlayer.pause()
                    else audioPlayer.play(mediaUrl)
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) Res.string.voice_pause else Res.string.voice_play),
                    tint = textColor
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f),
                color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.primary,
                trackColor = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            Spacer(Modifier.width(MuhabbetSpacing.XSmall))

            val displayTime = if (isPlaying || currentPosition > 0) {
                formatDuration((currentPosition / 1000).toInt())
            } else {
                formatDuration(durationSeconds ?: 0)
            }
            Text(
                text = displayTime,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }

        // Transcribe button
        if (speechTranscriber.isAvailable() && transcript == null && !isTranscribing) {
            Text(
                text = transcribeText,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f),
                modifier = Modifier.clickable {
                    isTranscribing = true
                    scope.launch {
                        try {
                            // Download audio and transcribe
                            val client = HttpClient()
                            val response = client.get(mediaUrl)
                            val bytes = response.bodyAsBytes()
                            client.close()
                            val result = speechTranscriber.transcribe(bytes)
                            transcript = result ?: transcriptFailedText
                        } catch (_: Exception) {
                            transcript = transcriptFailedText
                        }
                        isTranscribing = false
                        showTranscript = true
                    }
                }
            )
        }

        // Transcribing indicator
        if (isTranscribing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp,
                    color = textColor.copy(alpha = 0.6f)
                )
                Spacer(Modifier.width(MuhabbetSpacing.XSmall))
                Text(
                    text = transcribingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.6f)
                )
            }
        }

        // Transcript text
        AnimatedVisibility(visible = showTranscript && transcript != null) {
            Text(
                text = transcript ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.85f)
            )
        }
    }
}

private fun formatDuration(seconds: Int): String =
    DateTimeFormatter.formatDuration(seconds)
