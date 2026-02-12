package com.muhabbet.app.platform

import kotlinx.coroutines.flow.StateFlow

expect class AudioPlayer {
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>

    fun play(url: String)
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun release()
}

@androidx.compose.runtime.Composable
expect fun rememberAudioPlayer(): AudioPlayer
