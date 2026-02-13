package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.NSURL

actual class AudioPlayer {
    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPositionMs = MutableStateFlow(0L)
    actual val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    actual val durationMs: StateFlow<Long> = _durationMs

    private var player: AVAudioPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    @OptIn(ExperimentalForeignApi::class)
    actual fun play(url: String) {
        try {
            stop()
            val nsUrl = NSURL.URLWithString(url) ?: return
            val audioPlayer = AVAudioPlayer(contentsOfURL = nsUrl, error = null)
            audioPlayer.prepareToPlay()
            audioPlayer.play()
            player = audioPlayer

            _durationMs.value = (audioPlayer.duration * 1000).toLong()
            _isPlaying.value = true

            progressJob = scope.launch {
                while (_isPlaying.value) {
                    _currentPositionMs.value = (audioPlayer.currentTime * 1000).toLong()
                    if (!audioPlayer.isPlaying()) {
                        _isPlaying.value = false
                    }
                    delay(100)
                }
            }
        } catch (_: Exception) {
            // Audio playback failed
        }
    }

    actual fun pause() {
        player?.pause()
        _isPlaying.value = false
        progressJob?.cancel()
    }

    actual fun stop() {
        player?.stop()
        player = null
        _isPlaying.value = false
        _currentPositionMs.value = 0L
        progressJob?.cancel()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun seekTo(positionMs: Long) {
        player?.currentTime = positionMs / 1000.0
        _currentPositionMs.value = positionMs
    }

    actual fun release() {
        stop()
    }
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    return remember { AudioPlayer() }
}
