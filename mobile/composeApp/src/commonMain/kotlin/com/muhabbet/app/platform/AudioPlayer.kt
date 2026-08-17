package com.muhabbet.app.platform

import kotlinx.coroutines.flow.StateFlow

expect class AudioPlayer {
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>

    /**
     * The URL currently loaded into the player, or null when nothing is loaded.
     *
     * One [AudioPlayer] is shared by every voice bubble in a chat (`rememberAudioPlayer()` is
     * called once per screen). Without this, every bubble reads the same [isPlaying] /
     * [currentPositionMs] / [durationMs] regardless of which message they actually belong to — the
     * bubble for a message that ISN'T playing would show the position and duration of whichever
     * message IS. Callers compare this against their own media URL before trusting the other flows.
     */
    val currentUrl: StateFlow<String?>

    /** 1.0 = normal speed. Sticky across messages on purpose — matches how the shared player
     *  instance already carries state between bubbles. */
    val playbackSpeed: StateFlow<Float>

    /**
     * Starts playback of [url]. If [url] is already the loaded track (i.e. it was paused, not
     * stopped), resumes from the current position instead of reloading from zero.
     */
    fun play(url: String)
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun release()
}

@androidx.compose.runtime.Composable
expect fun rememberAudioPlayer(): AudioPlayer
