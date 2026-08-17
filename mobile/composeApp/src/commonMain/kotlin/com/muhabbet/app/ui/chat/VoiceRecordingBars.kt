package com.muhabbet.app.ui.chat

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.muhabbet.app.platform.AudioPlayer
import com.muhabbet.app.platform.RecordedAudio
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetIconButton
import com.muhabbet.designsystem.theme.MuhabbetElevation
import com.muhabbet.designsystem.theme.MuhabbetSizes
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import org.jetbrains.compose.resources.stringResource

/**
 * The composer row shown once [VoiceRecordingPhase.Locked] — the finger is already up, the
 * recording is still running hands-free, and the only way out is one of these two explicit taps.
 * Send here stops and uploads immediately: locking already asked for one deliberate action, and
 * making it ask for a second (send, then confirm a preview) would be the reverse of what locking is
 * for — hands-free recording is exactly the case where a fast, single-tap send matters most.
 * [VoiceRecordingPhase.Preview] is the deliberately slower path, for the plain release that never
 * asked for anything.
 */
@Composable
internal fun LockedRecordingBar(
    recordingSeconds: Int,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val discardDescription = stringResource(Res.string.voice_discard)
    val sendDescription = stringResource(Res.string.action_send)

    Surface(tonalElevation = MuhabbetElevation.Level2, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MuhabbetSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Muhabbet.icons.Lock,
                contentDescription = stringResource(Res.string.voice_locked),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(MuhabbetSizes.IconMedium)
            )
            Spacer(Modifier.width(MuhabbetSpacing.Medium))
            RecordingDot()
            Spacer(Modifier.width(MuhabbetSpacing.Small))
            Text(
                text = DateTimeFormatter.formatDuration(recordingSeconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCancel, modifier = Modifier.size(MuhabbetSizes.MinTouchTarget)) {
                Icon(Muhabbet.icons.Delete, contentDescription = discardDescription, tint = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.width(MuhabbetSpacing.XSmall))
            FilledIconButton(
                onClick = onSend,
                modifier = Modifier.size(MuhabbetSizes.MinTouchTarget),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Muhabbet.icons.Send, contentDescription = sendDescription, modifier = Modifier.size(MuhabbetSizes.IconMedium))
            }
        }
    }
}

/**
 * The composer row shown once [VoiceRecordingPhase.Preview] — a recording that was simply released
 * (no cancel, no lock). This is the state #601 was filed over: previously, releasing the record
 * button had exactly one outcome, and it was sending. Nothing here is sent until Send is tapped;
 * Discard deletes the take and returns to the ordinary composer.
 *
 * Plays back through the same shared [AudioPlayer] instance the chat's message bubbles use
 * (`rememberAudioPlayer()` is called once per screen in `ChatScreen`) — reusing it rather than
 * standing up a second player for a file that has not been uploaded yet. [AudioPlayer.play] takes
 * any URL it can hand to the platform decoder, and both platform decoders accept a `file://` URI for
 * a local path, so the not-yet-sent recording plays the same way an already-sent one would once it
 * has a `https://` media URL — no changes needed to the player itself.
 */
@Composable
internal fun VoicePreviewBar(
    audio: RecordedAudio,
    audioPlayer: AudioPlayer,
    onDiscard: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentUrl by audioPlayer.currentUrl.collectAsState()
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val currentPosition by audioPlayer.currentPositionMs.collectAsState()

    val localUrl = remember(audio.localFilePath) { audio.localFilePath?.let { "file://$it" } }
    // Guards against a stray previous/next message bubble having repurposed the shared player
    // while this bar is on screen — see the class doc on AudioPlayer.currentUrl.
    val isThisPlaying = isPlaying && currentUrl == localUrl

    val discardDescription = stringResource(Res.string.voice_discard)
    val sendDescription = stringResource(Res.string.action_send)
    val playDescription = stringResource(Res.string.voice_play)
    val pauseDescription = stringResource(Res.string.voice_pause)

    Surface(tonalElevation = MuhabbetElevation.Level2, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MuhabbetSpacing.Small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDiscard, modifier = Modifier.size(MuhabbetSizes.MinTouchTarget)) {
                Icon(Muhabbet.icons.Delete, contentDescription = discardDescription, tint = MaterialTheme.colorScheme.error)
            }

            MuhabbetIconButton(
                icon = if (isThisPlaying) Muhabbet.icons.Pause else Muhabbet.icons.Play,
                contentDescription = if (isThisPlaying) pauseDescription else playDescription,
                onClick = {
                    if (isThisPlaying) audioPlayer.pause()
                    else localUrl?.let { audioPlayer.play(it) }
                },
                enabled = localUrl != null
            )

            val displaySeconds = if (isThisPlaying && currentPosition > 0) (currentPosition / 1000).toInt() else audio.durationSeconds
            Text(
                text = DateTimeFormatter.formatDuration(displaySeconds),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = MuhabbetSpacing.Small)
            )

            FilledIconButton(
                onClick = onSend,
                modifier = Modifier.size(MuhabbetSizes.MinTouchTarget),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Muhabbet.icons.Send, contentDescription = sendDescription, modifier = Modifier.size(MuhabbetSizes.IconMedium))
            }
        }
    }
}
