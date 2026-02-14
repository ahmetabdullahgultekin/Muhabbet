package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

actual class AudioRecorder {
    private var recorder: AVAudioRecorder? = null
    private var outputPath: String? = null
    private var recording = false
    private var recordingStartTime: Long = 0L

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
            recordingStartTime = platform.Foundation.NSDate().timeIntervalSince1970.toLong() * 1000
        } catch (_: Exception) {
            recording = false
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun stopRecording(): RecordedAudio? {
        recorder?.stop()
        recording = false
        val path = outputPath ?: return null
        recorder = null

        val session = AVAudioSession.sharedInstance()
        session.setActive(false, withOptions = AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation, error = null)

        val durationMs = (platform.Foundation.NSDate().timeIntervalSince1970.toLong() * 1000) - recordingStartTime
        val durationSecs = (durationMs / 1000).toInt().coerceAtLeast(1)

        // Read file bytes
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        val bytes = data.toByteArray()

        return RecordedAudio(
            bytes = bytes,
            mimeType = "audio/mp4",
            durationSeconds = durationSecs
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toByteArray(): ByteArray {
        val size = this.length.toInt()
        val bytes = ByteArray(size)
        if (size > 0) {
            bytes.usePinned { pinned ->
                memcpy(pinned.addressOf(0), this@toByteArray.bytes, this@toByteArray.length)
            }
        }
        return bytes
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
