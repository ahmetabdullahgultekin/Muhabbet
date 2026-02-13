package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL

actual class AudioRecorder {
    private var recorder: AVAudioRecorder? = null
    private var outputPath: String? = null
    private var recording = false

    @OptIn(ExperimentalForeignApi::class)
    actual fun startRecording() {
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryRecord, error = null)
            session.setActive(true, error = null)

            val path = NSTemporaryDirectory() + "voice_message.m4a"
            outputPath = path
            val url = NSURL.fileURLWithPath(path)

            val settings = mapOf<Any?, Any?>(
                "AVFormatIDKey" to 1633772320L, // kAudioFormatMPEG4AAC
                "AVSampleRateKey" to 44100.0,
                "AVNumberOfChannelsKey" to 1L,
                "AVEncoderAudioQualityKey" to 1L // AVAudioQualityMedium
            )

            val audioRecorder = AVAudioRecorder(URL = url, settings = settings, error = null)
            audioRecorder.prepareToRecord()
            audioRecorder.record()
            recorder = audioRecorder
            recording = true
        } catch (_: Exception) {
            recording = false
        }
    }

    actual fun stopRecording(): RecordedAudio? {
        recorder?.stop()
        recording = false
        val path = outputPath ?: return null
        recorder = null

        val session = AVAudioSession.sharedInstance()
        session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)

        return RecordedAudio(filePath = path, durationMs = 0L) // duration calculated on upload
    }

    actual fun cancelRecording() {
        recorder?.stop()
        recorder?.deleteRecording()
        recorder = null
        recording = false
    }

    actual fun isRecording(): Boolean = recording

    actual fun hasPermission(): Boolean {
        return AVAudioSession.sharedInstance().recordPermission == 1L // AVAudioSessionRecordPermissionGranted
    }
}

@Composable
actual fun rememberAudioRecorder(): AudioRecorder {
    return remember { AudioRecorder() }
}

@Composable
actual fun rememberAudioPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit {
    return {
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            onResult(granted)
        }
    }
}
