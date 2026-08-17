package com.muhabbet.app.platform

/**
 * Platform-specific speech-to-text transcription for voice messages.
 *
 * Android: on-device SpeechRecognizer, gated to API 33+ — see `SpeechTranscriber.android.kt`
 * for why (issue #381).
 * iOS: Apple SFSpeechRecognizer (Speech framework), file-based ([transcribe] never opens the
 * live microphone on either platform).
 *
 * Primary language: Turkish (tr-TR).
 *
 * There is no server-side transcription fallback — none exists in the backend. [isAvailable]
 * returning `false` (or [transcribe] returning `null`) is the end of the story for that message;
 * the caller (`VoiceBubble.kt`) shows a failure string, not a second attempt elsewhere.
 */
expect class SpeechTranscriber {
    /**
     * Whether on-device transcription can be offered right now, without opening the
     * microphone, on this device. Callers should hide the transcribe control when this is
     * `false` rather than show one that can only fail.
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
