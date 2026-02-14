package com.muhabbet.app.platform

/**
 * Platform-specific speech-to-text transcription for voice messages.
 *
 * Android: Uses Android SpeechRecognizer or MediaCodec + on-device ML.
 * iOS: Uses Apple SFSpeechRecognizer (Speech framework).
 *
 * Primary language: Turkish (tr-TR).
 * Falls back to server-side transcription if on-device fails.
 */
expect class SpeechTranscriber {
    /**
     * Check if on-device transcription is available.
     */
    fun isAvailable(): Boolean

    /**
     * Transcribe audio bytes to text.
     * Returns null if transcription fails or is unavailable.
     *
     * @param audioBytes The audio data (OGG/OPUS on Android, M4A/AAC on iOS)
     * @param languageCode BCP-47 language code (default: "tr-TR")
     */
    suspend fun transcribe(audioBytes: ByteArray, languageCode: String = "tr-TR"): String?
}
