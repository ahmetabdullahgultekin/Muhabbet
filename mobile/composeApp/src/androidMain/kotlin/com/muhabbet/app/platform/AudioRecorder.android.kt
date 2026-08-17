package com.muhabbet.app.platform

import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import java.io.File

actual class AudioRecorder(private val context: android.content.Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTimeMs: Long = 0
    private var recording = false

    actual fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    actual fun startRecording() {
        // Defensive: a previous recording that was stopped (for preview) but never sent or
        // discarded should not leak. Normal usage always goes through discardPreview() first —
        // the composer cannot show the mic again while a preview is on screen — but a fresh
        // recording must never inherit a stale handle either way.
        outputFile?.takeIf { it.exists() }?.delete()

        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.ogg")
        outputFile = file

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioChannels(1)
            setAudioSamplingRate(16000)
            setAudioEncodingBitRate(32000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        startTimeMs = System.currentTimeMillis()
        recording = true
    }

    actual fun stopRecording(): RecordedAudio? {
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            recording = false

            val durationMs = System.currentTimeMillis() - startTimeMs
            val durationSecs = (durationMs / 1000).toInt().coerceAtLeast(1)

            // The file is kept on disk (not deleted here) so a caller can preview it before
            // deciding to send — see RecordedAudio.localFilePath. discardPreview() is the cleanup.
            outputFile?.let { file ->
                val bytes = file.readBytes()
                RecordedAudio(
                    bytes = bytes,
                    mimeType = "audio/ogg",
                    durationSeconds = durationSecs,
                    localFilePath = file.absolutePath
                )
            }
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            recording = false
            outputFile?.delete()
            outputFile = null
            null
        }
    }

    actual fun cancelRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) { }
        recorder?.release()
        recorder = null
        recording = false
        outputFile?.delete()
        outputFile = null
    }

    actual fun discardPreview() {
        outputFile?.delete()
        outputFile = null
    }

    actual fun isRecording(): Boolean = recording
}

@Composable
actual fun rememberAudioRecorder(): AudioRecorder {
    val context = LocalContext.current
    return remember { AudioRecorder(context) }
}

@Composable
actual fun rememberAudioPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onResult(it) }
    return { launcher.launch(android.Manifest.permission.RECORD_AUDIO) }
}
