package com.muhabbet.app.platform

data class RecordedAudio(
    val bytes: ByteArray,
    val mimeType: String,
    val durationSeconds: Int
)

expect class AudioRecorder {
    fun startRecording()
    fun stopRecording(): RecordedAudio?
    fun cancelRecording()
    fun isRecording(): Boolean
    fun hasPermission(): Boolean
}

@androidx.compose.runtime.Composable
expect fun rememberAudioRecorder(): AudioRecorder

@androidx.compose.runtime.Composable
expect fun rememberAudioPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit
