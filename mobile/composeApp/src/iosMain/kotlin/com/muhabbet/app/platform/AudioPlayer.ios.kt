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

    private val _currentUrl = MutableStateFlow<String?>(null)
    actual val currentUrl: StateFlow<String?> = _currentUrl

    private val _playbackSpeed = MutableStateFlow(1.0f)
    actual val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private var player: AVAudioPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    @OptIn(ExperimentalForeignApi::class)
    actual fun play(url: String) {
        try {
            val existing = player
            if (existing != null && _currentUrl.value == url) {
                // Same track, just paused — AVAudioPlayer.play() resumes from currentTime on its
                // own, so this only needs to re-apply rate and restart the polling loop that pause()
                // cancelled. The old unconditional reload below threw the paused position away.
                existing.rate = _playbackSpeed.value
                existing.play()
                _isPlaying.value = true
                startProgressUpdates(existing)
                return
            }

            stop()
            val nsUrl = NSURL.URLWithString(url) ?: return
            val audioPlayer = AVAudioPlayer(contentsOfURL = nsUrl, error = null)
            audioPlayer.enableRate = true
            audioPlayer.prepareToPlay()
            audioPlayer.play()
            audioPlayer.rate = _playbackSpeed.value
            player = audioPlayer
            _currentUrl.value = url

            _durationMs.value = (audioPlayer.duration * 1000).toLong()
            _isPlaying.value = true

            startProgressUpdates(audioPlayer)
        } catch (_: Exception) {
            // Audio playback failed
        }
    }

    private fun startProgressUpdates(audioPlayer: AVAudioPlayer) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value) {
                _currentPositionMs.value = (audioPlayer.currentTime * 1000).toLong()
                if (!audioPlayer.isPlaying()) {
                    _isPlaying.value = false
                }
                delay(100)
            }
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
        _durationMs.value = 0L
        _currentUrl.value = null
        progressJob?.cancel()
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun seekTo(positionMs: Long) {
        player?.currentTime = positionMs / 1000.0
        _currentPositionMs.value = positionMs
    }

    actual fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        // Takes effect immediately per AVAudioPlayer docs, whether or not playback is in progress.
        player?.rate = speed
    }

    actual fun release() {
        stop()
    }
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    return remember { AudioPlayer() }
}
