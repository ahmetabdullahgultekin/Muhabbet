package com.muhabbet.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.muhabbet.app.platform.AudioPlayer
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun VoiceBubble(
    mediaUrl: String,
    durationSeconds: Int?,
    isOwn: Boolean,
    audioPlayer: AudioPlayer,
    modifier: Modifier = Modifier
) {
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val currentPosition by audioPlayer.currentPositionMs.collectAsState()
    val duration by audioPlayer.durationMs.collectAsState()

    val totalDuration = durationSeconds?.let { it * 1000L } ?: duration
    val progress = if (totalDuration > 0) (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                tint = if (isOwn) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
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

        Spacer(Modifier.width(4.dp))

        val displayTime = if (isPlaying || currentPosition > 0) {
            formatDuration((currentPosition / 1000).toInt())
        } else {
            formatDuration(durationSeconds ?: 0)
        }
        Text(
            text = displayTime,
            style = MaterialTheme.typography.labelSmall,
            color = if (isOwn) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}
