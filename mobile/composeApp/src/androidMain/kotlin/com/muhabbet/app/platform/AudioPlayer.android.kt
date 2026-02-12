package com.muhabbet.app.platform

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

actual class AudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    private var updateJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isPlaying = MutableStateFlow(false)
    actual val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPositionMs = MutableStateFlow(0L)
    actual val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    actual val durationMs: StateFlow<Long> = _durationMs

    actual fun play(url: String) {
        stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener { mp ->
                _durationMs.value = mp.duration.toLong()
                mp.start()
                _isPlaying.value = true
                startPositionUpdates()
            }
            setOnCompletionListener {
                _isPlaying.value = false
                _currentPositionMs.value = _durationMs.value
                updateJob?.cancel()
            }
            setOnErrorListener { _, _, _ ->
                _isPlaying.value = false
                updateJob?.cancel()
                true
            }
            prepareAsync()
        }
    }

    actual fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                updateJob?.cancel()
            }
        }
    }

    actual fun stop() {
        updateJob?.cancel()
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (_: Exception) { }
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPositionMs.value = 0
    }

    actual fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _currentPositionMs.value = positionMs
    }

    actual fun release() {
        stop()
    }

    private fun startPositionUpdates() {
        updateJob?.cancel()
        updateJob = scope.launch {
            while (_isPlaying.value) {
                mediaPlayer?.let {
                    try {
                        _currentPositionMs.value = it.currentPosition.toLong()
                    } catch (_: Exception) { }
                }
                delay(200)
            }
        }
    }
}

@Composable
actual fun rememberAudioPlayer(): AudioPlayer {
    val player = remember { AudioPlayer() }
    DisposableEffect(Unit) {
        onDispose { player.release() }
    }
    return player
}
