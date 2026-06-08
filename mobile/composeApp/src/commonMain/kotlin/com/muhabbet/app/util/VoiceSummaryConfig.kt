package com.muhabbet.app.util

/**
 * Feature flag for the Turkish voice-note → transcript + short summary feature (D5).
 *
 * DEFAULT = false → the voice bubble behaves exactly as before: only the on-device transcript
 * is shown (existing behavior). No "Özet" line is rendered and the summarizer is never invoked.
 *
 * When true:
 *  - After transcription succeeds, [TurkishExtractiveSummarizer.summarize] runs on-device against
 *    the transcript and a one-line "Özet" is shown beneath the transcript.
 *
 * The summarizer itself is a pure on-device extractive algorithm — no network, no LLM, no mock.
 * This is intentionally a compile-time constant (KISS): there is no runtime UI to toggle it yet.
 */
object VoiceSummaryConfig {
    /** Master switch for the transcript-summary line. Keep false until reviewed/promoted. */
    const val ENABLED: Boolean = false
}
