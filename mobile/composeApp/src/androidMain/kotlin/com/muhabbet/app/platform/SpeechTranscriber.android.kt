package com.muhabbet.app.platform

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.speech.SpeechRecognizer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Android speech transcription using Android's built-in SpeechRecognizer.
 *
 * Writes audio bytes to a temp file, then uses the SpeechRecognizer API
 * for on-device transcription. Turkish (tr-TR) is well-supported on
 * most Android devices with Google's speech models.
 *
 * Fallback: Returns null if transcription is unavailable, letting the
 * caller fall back to server-side transcription.
 */
actual class SpeechTranscriber(private val context: Context) {

    actual fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    actual suspend fun transcribe(audioBytes: ByteArray, languageCode: String): String? {
        if (!isAvailable()) return null

        return try {
            // Write to temp file for processing
            val tempFile = File(context.cacheDir, "transcribe_${System.currentTimeMillis()}.ogg")
            tempFile.writeBytes(audioBytes)

            val result = performTranscription(tempFile, languageCode)
            tempFile.delete()
            result
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun performTranscription(audioFile: File, languageCode: String): String? {
        return suspendCoroutine { continuation ->
            try {
                val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                    putExtra("android.speech.extra.AUDIO_SOURCE", audioFile.absolutePath)
                }

                recognizer.setRecognitionListener(object : android.speech.RecognitionListener {
                    override fun onResults(results: android.os.Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        recognizer.destroy()
                        continuation.resume(matches?.firstOrNull())
                    }

                    override fun onError(error: Int) {
                        recognizer.destroy()
                        continuation.resume(null)
                    }

                    override fun onReadyForSpeech(params: android.os.Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: android.os.Bundle?) {}
                    override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                })

                recognizer.startListening(intent)
            } catch (_: Exception) {
                continuation.resume(null)
            }
        }
    }
}
