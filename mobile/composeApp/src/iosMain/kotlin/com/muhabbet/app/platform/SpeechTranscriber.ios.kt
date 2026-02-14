package com.muhabbet.app.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechURLRecognitionRequest
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * iOS speech transcription using Apple's SFSpeechRecognizer (Speech framework).
 *
 * Uses on-device speech recognition when available (iOS 13+).
 * Turkish (tr-TR) is supported on most iOS devices.
 * Requires NSMicrophoneUsageDescription and NSSpeechRecognitionUsageDescription
 * in Info.plist.
 */
actual class SpeechTranscriber {

    actual fun isAvailable(): Boolean {
        val locale = platform.Foundation.NSLocale("tr-TR")
        val recognizer = SFSpeechRecognizer(locale = locale)
        return recognizer?.isAvailable() == true &&
            SFSpeechRecognizer.authorizationStatus() == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun transcribe(audioBytes: ByteArray, languageCode: String): String? {
        val locale = platform.Foundation.NSLocale(languageCode)
        val recognizer = SFSpeechRecognizer(locale = locale)
        if (recognizer?.isAvailable() != true) return null

        return try {
            // Write audio to temp file
            val path = NSTemporaryDirectory() + "transcribe_${platform.Foundation.NSDate().timeIntervalSince1970.toLong()}.m4a"
            val data = audioBytes.toNSData()
            data.writeToFile(path, atomically = true)

            val url = NSURL.fileURLWithPath(path)
            val request = SFSpeechURLRecognitionRequest(uRL = url)

            suspendCoroutine { continuation ->
                recognizer.recognitionTaskWithRequest(request) { result, error ->
                    if (error != null || result == null) {
                        continuation.resume(null)
                        return@recognitionTaskWithRequest
                    }
                    if (result.isFinal()) {
                        continuation.resume(result.bestTranscription.formattedString)
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun ByteArray.toNSData(): NSData {
        return this.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
        }
    }
}
