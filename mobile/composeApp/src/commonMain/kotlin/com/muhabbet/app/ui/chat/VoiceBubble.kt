package com.muhabbet.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.muhabbet.designsystem.theme.LocalSemanticColors
import com.muhabbet.designsystem.theme.MuhabbetAlphas
import com.muhabbet.designsystem.theme.MuhabbetSpacing
import com.muhabbet.app.util.DateTimeFormatter
import com.muhabbet.composeapp.generated.resources.Res
import com.muhabbet.composeapp.generated.resources.*
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.muhabbet.designsystem.Muhabbet
import com.muhabbet.designsystem.components.MuhabbetIconButton

@Composable
fun VoiceBubble(
    mediaUrl: String,
    durationSeconds: Int?,
    isOwn: Boolean,
    audioPlayer: AudioPlayer,
    modifier: Modifier = Modifier,
    speechTranscriber: SpeechTranscriber = koinInject()
) {
    val globalIsPlaying by audioPlayer.isPlaying.collectAsState()
    val globalPosition by audioPlayer.currentPositionMs.collectAsState()
    val globalDuration by audioPlayer.durationMs.collectAsState()
    val currentUrl by audioPlayer.currentUrl.collectAsState()
    val playbackSpeed by audioPlayer.playbackSpeed.collectAsState()
    val scope = rememberCoroutineScope()

    // One AudioPlayer is shared across every voice bubble in the chat (`rememberAudioPlayer()` is
    // called once per screen), so its position/duration/isPlaying belong to whichever message is
    // actually loaded — not necessarily this one. Without this check, every OTHER bubble in the
    // list showed the currently-playing message's progress and duration instead of its own, and
    // its Play button would silently pause the wrong message.
    val isActive = currentUrl == mediaUrl
    val isPlaying = isActive && globalIsPlaying
    val currentPosition = if (isActive) globalPosition else 0L
    val liveDuration = if (isActive) globalDuration else 0L

    var transcript by remember { mutableStateOf<String?>(null) }
    var isTranscribing by remember { mutableStateOf(false) }
    var showTranscript by remember { mutableStateOf(false) }

    val transcribeText = stringResource(Res.string.voice_transcribe)
    val transcribingText = stringResource(Res.string.voice_transcribing)
    val transcriptFailedText = stringResource(Res.string.voice_transcript_failed)
    val playbackSpeedLabel = stringResource(Res.string.voice_playback_speed_change)

    // `durationSeconds` is what the server knows about this message; it is null for every message
    // today (the shared Message model has no duration field yet — see PR notes), so this falls
    // back to the player's own decoder-reported duration, which becomes available the moment this
    // bubble's audio has been loaded even once.
    val totalDuration = durationSeconds?.let { it * 1000L } ?: liveDuration

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    // A seek the user released before the player had finished preparing (e.g. dragging a message
    // that was never played) — applied once a real duration shows up, below.
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }

    val sliderPosition = when {
        isDragging -> dragFraction
        totalDuration > 0 -> (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }

    LaunchedEffect(mediaUrl, isActive, liveDuration) {
        val fraction = pendingSeekFraction
        if (fraction != null && isActive && liveDuration > 0) {
            audioPlayer.seekTo((fraction * liveDuration).toLong())
            pendingSeekFraction = null
        }
    }

    // #517, same shape as the poll: the ground behind this row is the bubble, not `primary`, so the
    // foreground has to come off the bubble's own pair.
    val bubble = if (isOwn) LocalSemanticColors.current.bubbleOwn
        else LocalSemanticColors.current.bubbleOther
    val textColor = bubble.content
    // The captions under the scrubber are the same secondary mark every timestamp uses. They were
    // `textColor` at 0.6-0.7 alpha, which is an unmeasured colour by construction: nothing knows
    // what a faded foreground lands at over a given bubble, so nothing could check it.
    val captionColor = LocalSemanticColors.current.secondaryText

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MuhabbetSpacing.XSmall)
        ) {
            MuhabbetIconButton(
                icon = if (isPlaying) Muhabbet.icons.Pause else Muhabbet.icons.Play,
                contentDescription = stringResource(if (isPlaying) Res.string.voice_pause else Res.string.voice_play),
                onClick = {
                    if (isPlaying) audioPlayer.pause()
                    else audioPlayer.play(mediaUrl)
                },
                modifier = Modifier.size(48.dp),
                tint = textColor
            )

            Slider(
                value = sliderPosition,
                onValueChange = { value ->
                    isDragging = true
                    dragFraction = value
                    if (!isActive) {
                        // Nothing of this message is loaded yet — start it so the real duration
                        // (and something to seek within) becomes available. seekTo below applies
                        // once it does.
                        audioPlayer.play(mediaUrl)
                    }
                },
                onValueChangeFinished = {
                    isDragging = false
                    // Read the player directly (StateFlow.value) rather than the values collected
                    // above: play() just triggered on this same gesture may not have flowed through
                    // a recomposition yet by the time the finger lifts.
                    val activeNow = audioPlayer.currentUrl.value == mediaUrl
                    val totalNow = audioPlayer.durationMs.value
                    if (activeNow && totalNow > 0) {
                        audioPlayer.seekTo((dragFraction * totalNow).toLong())
                    } else {
                        pendingSeekFraction = dragFraction
                    }
                },
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = textColor,
                    activeTrackColor = textColor,
                    inactiveTrackColor = textColor.copy(alpha = MuhabbetAlphas.ProgressTrack)
                )
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
                color = captionColor
            )
        }

        // Playback speed — sticky on the shared player, so picking 1.5x here carries to the next
        // voice message played too, same as the platform players it wraps.
        Text(
            text = formatSpeed(playbackSpeed),
            style = MaterialTheme.typography.labelSmall,
            color = captionColor,
            modifier = Modifier.clickable(onClickLabel = playbackSpeedLabel) {
                audioPlayer.setPlaybackSpeed(nextSpeed(playbackSpeed))
            }
        )

        // Transcribe button
        if (speechTranscriber.isAvailable() && transcript == null && !isTranscribing) {
            Text(
                text = transcribeText,
                style = MaterialTheme.typography.labelSmall,
                color = captionColor,
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
                    color = captionColor
                )
                Spacer(Modifier.width(MuhabbetSpacing.XSmall))
                Text(
                    text = transcribingText,
                    style = MaterialTheme.typography.labelSmall,
                    color = captionColor
                )
            }
        }

        // Transcript text
        AnimatedVisibility(visible = showTranscript && transcript != null) {
            Text(
                text = transcript ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = textColor
            )
        }
    }
}

private fun formatDuration(seconds: Int): String =
    DateTimeFormatter.formatDuration(seconds)

/** 1x / 1.5x / 2x — the standard voice-message cycle. Not a translated phrase (a formatted
 *  multiplier reads the same in every locale), so this stays out of strings.xml, matching how
 *  [formatDuration] already formats "0:32" without a string resource. */
private val PlaybackSpeeds = listOf(1.0f, 1.5f, 2.0f)

private fun nextSpeed(current: Float): Float {
    val index = PlaybackSpeeds.indexOf(current)
    return PlaybackSpeeds[(index + 1).mod(PlaybackSpeeds.size)]
}

private fun formatSpeed(speed: Float): String {
    val rounded = (speed * 10).toInt()
    val whole = rounded / 10
    val tenth = rounded % 10
    return if (tenth == 0) "${whole}x" else "${whole}.${tenth}x"
}
