package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlin.time.Clock
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

actual class AudioRecorder {
    private var recorder: AVAudioRecorder? = null
    private var outputPath: String? = null
    private var recording = false
    private var recordingStartMs: Long = 0L

    @OptIn(ExperimentalForeignApi::class)
    actual fun startRecording() {
        try {
            val session = AVAudioSession.sharedInstance()
            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                session.setCategory(AVAudioSessionCategoryRecord, error = err.ptr)
                // Note: setActive removed — API binding changed in Kotlin/Native 2.3.x
                // Audio session category alone is sufficient in most recording scenarios
            }

            val path = NSTemporaryDirectory() + "voice_message.m4a"
            outputPath = path
            val url = NSURL.fileURLWithPath(path)

            val settings = mapOf<Any?, Any?>(
                "AVFormatIDKey" to 1633772320L, // kAudioFormatMPEG4AAC
                "AVSampleRateKey" to 44100.0,
                "AVNumberOfChannelsKey" to 1L,
                "AVEncoderAudioQualityKey" to 1L // AVAudioQualityMedium
            )

            memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                val audioRecorder = AVAudioRecorder(uRL = url, settings = settings, error = err.ptr)
                    ?: return
                audioRecorder.prepareToRecord()
                audioRecorder.record()
                recorder = audioRecorder
            }

            recording = true
            recordingStartMs = Clock.System.now().toEpochMilliseconds()
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

        val durationMs = Clock.System.now().toEpochMilliseconds() - recordingStartMs
        val durationSecs = (durationMs / 1000).toInt().coerceAtLeast(1)

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
        return AVAudioSession.sharedInstance().recordPermission == AVAudioSessionRecordPermissionGranted
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
