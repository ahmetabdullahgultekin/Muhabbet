package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

actual class AudioRecorder {
    actual fun startRecording() { /* iOS stub */ }
    actual fun stopRecording(): RecordedAudio? = null
    actual fun cancelRecording() { /* iOS stub */ }
    actual fun isRecording(): Boolean = false
}

@Composable
actual fun rememberAudioRecorder(): AudioRecorder {
    return remember { AudioRecorder() }
}
