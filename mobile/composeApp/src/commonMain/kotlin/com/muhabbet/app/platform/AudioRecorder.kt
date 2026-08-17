package com.muhabbet.app.platform

data class RecordedAudio(
    val bytes: ByteArray,
    val mimeType: String,
    val durationSeconds: Int,
    /**
     * Where the recording still sits on disk, for local preview playback before it is uploaded.
     *
     * [AudioRecorder.stopRecording] used to delete this file the moment it had read the bytes back
     * out of it — fine when the only thing release could do was send, but #601 adds a preview step
     * with its own player, and a player needs a file to point at. Null only if the platform recorder
     * could not resolve a path (should not happen on either implementation); callers must not assume
     * it is non-null.
     *
     * The caller owns the cleanup: call [AudioRecorder.discardPreview] once the recording has either
     * been sent or discarded, so this temp file does not linger.
     */
    val localFilePath: String? = null
)

expect class AudioRecorder {
    fun startRecording()
    fun stopRecording(): RecordedAudio?
    fun cancelRecording()
    fun isRecording(): Boolean
    fun hasPermission(): Boolean

    /**
     * Deletes the file [stopRecording] left on disk for preview playback.
     *
     * Call this after the previewed recording has actually been sent (the upload already read the
     * bytes from memory, not from this file) or after the user discarded it. A no-op if there is
     * nothing left to clean up — safe to call defensively.
     */
    fun discardPreview()
}

@androidx.compose.runtime.Composable
expect fun rememberAudioRecorder(): AudioRecorder

@androidx.compose.runtime.Composable
expect fun rememberAudioPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit
