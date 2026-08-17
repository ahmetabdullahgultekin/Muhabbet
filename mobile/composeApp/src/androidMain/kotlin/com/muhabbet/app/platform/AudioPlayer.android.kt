package com.muhabbet.app.platform

import android.media.MediaPlayer
import android.media.PlaybackParams
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

    private val _currentUrl = MutableStateFlow<String?>(null)
    actual val currentUrl: StateFlow<String?> = _currentUrl

    private val _playbackSpeed = MutableStateFlow(1.0f)
    actual val playbackSpeed: StateFlow<Float> = _playbackSpeed

    actual fun play(url: String) {
        val existing = mediaPlayer
        if (existing != null && _currentUrl.value == url) {
            // Same track, just paused — resume in place. Reloading (the old unconditional
            // `stop(); MediaPlayer()...` path) reset position to 0 on every tap of Play, which
            // silently threw away whatever the user had just seeked to.
            try {
                applySpeed(existing)
                existing.start()
                _isPlaying.value = true
                startPositionUpdates()
                return
            } catch (_: IllegalStateException) {
                // Player can't resume from this state (e.g. playback had already completed) —
                // fall through to a full reload below.
            }
        }

        stop()
        _currentUrl.value = url
        mediaPlayer = MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener { mp ->
                _durationMs.value = mp.duration.toLong()
                applySpeed(mp)
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
        _durationMs.value = 0
        _currentUrl.value = null
    }

    actual fun seekTo(positionMs: Long) {
        mediaPlayer?.seekTo(positionMs.toInt())
        _currentPositionMs.value = positionMs
    }

    actual fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        // Not every player state accepts new params (e.g. still preparing) — the stored value is
        // re-applied in play()'s onPreparedListener/resume path once it does.
        mediaPlayer?.let { mp -> try { applySpeed(mp) } catch (_: IllegalStateException) { } }
    }

    /** setSpeed alone also shifts pitch (the "chipmunk" effect on fast-forward); setPitch(1f)
     *  keeps voice pitch natural, which is what a playback-speed control is expected to do. */
    private fun applySpeed(mp: MediaPlayer) {
        if (_playbackSpeed.value == 1.0f) return
        mp.playbackParams = PlaybackParams().setSpeed(_playbackSpeed.value).setPitch(1f)
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
