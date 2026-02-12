package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class AudioPlayer {
    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPositionMs = MutableStateFlow(0L)
    actual val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    actual val durationMs: StateFlow<Long> = _durationMs

    actual fun play(url: String) { /* iOS stub */ }
    actual fun pause() { /* iOS stub */ }
    actual fun stop() { /* iOS stub */ }
    actual fun seekTo(positionMs: Long) { /* iOS stub */ }
    actual fun release() { /* iOS stub */ }
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    return remember { AudioPlayer() }
}
