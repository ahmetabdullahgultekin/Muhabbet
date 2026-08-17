package com.muhabbet.app.platform

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Android speech transcription using Android's on-device SpeechRecognizer.
 *
 * Feeding the recognizer a *file* instead of the live microphone requires
 * `RecognizerIntent.EXTRA_AUDIO_SOURCE`, which only exists from API 33 (Android 13) and only
 * accepts raw 16-bit PCM through a [ParcelFileDescriptor] — not a file path, and not the
 * OGG/Opus bytes the voice recorder actually produces. Below API 33 there is no supported
 * mechanism to hand the recognizer a file at all, and this class does not try: [isAvailable]
 * returns `false`, which hides the "Transcribe" button in `VoiceBubble.kt` entirely rather than
 * offering a control that either crashes or opens the microphone.
 *
 * At API 33+: [transcribe] decodes the recorded OGG/Opus bytes to raw PCM ([decodeToPcm]), then
 * streams that PCM into the recognizer through a pipe ([recognizeFromPcm]) using
 * `EXTRA_AUDIO_SOURCE`. The live microphone is never opened by this class.
 *
 * History (issue #381): the previous implementation called `createOnDeviceSpeechRecognizer`
 * (API 31) with no `SDK_INT` guard, which crashed with `NoSuchMethodError` on API 26-30 — an
 * `Error`, not caught by `catch (Exception)`. On every version it also passed the audio file as
 * a `String` under `"android.speech.extra.AUDIO_SOURCE"`, a key that only accepts a
 * `ParcelFileDescriptor` (API 33). The string was silently ignored, so the source was never
 * set and `startListening` opened the live microphone and recorded the room instead of
 * transcribing the message — the "transcription failed" the user saw was that silent room
 * being rejected as speech.
 */
actual class SpeechTranscriber(private val context: Context) {

    actual fun isAvailable(): Boolean {
        // Kept inline (not delegated to a helper) so this bound sits next to every API 33 call
        // below it — see the class doc for why 33, not 31 (createOnDeviceSpeechRecognizer's own
        // minimum): 31-32 has the on-device recognizer but no file-source extra, so using it
        // there would still open the mic.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } catch (_: Throwable) {
            // Defensive: some OEM images advertise an SDK_INT they don't fully implement.
            false
        }
    }

    actual suspend fun transcribe(audioBytes: ByteArray, languageCode: String): String? {
        if (!isAvailable()) return null

        return try {
            val pcm = withContext(Dispatchers.IO) {
                val tempFile = File(context.cacheDir, "transcribe_${System.currentTimeMillis()}.ogg")
                try {
                    tempFile.writeBytes(audioBytes)
                    decodeToPcm(tempFile)
                } finally {
                    tempFile.delete()
                }
            } ?: return null

            // SpeechRecognizer must be created and driven from a thread that has a Looper — the
            // main thread, here — while decoding above runs off it so it doesn't jank the UI.
            withContext(Dispatchers.Main) {
                recognizeFromPcm(pcm, languageCode)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Decodes an OGG/Opus voice message into raw 16-bit PCM. `EXTRA_AUDIO_SOURCE` only accepts
     * uncompressed PCM, not the Opus container `AudioRecorder.android.kt` records, so this has
     * to happen before the recognizer is involved at all.
     */
    private fun decodeToPcm(file: File): DecodedPcm? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return null
            extractor.selectTrack(trackIndex)

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(trackFormat, null, null, 0)
                codec.start()
                drainDecoder(codec, extractor, fallbackFormat = trackFormat)
            } finally {
                codec.stop()
                codec.release()
            }
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    /** Runs the decode loop to completion and collects the resulting PCM bytes. */
    private fun drainDecoder(codec: MediaCodec, extractor: MediaExtractor, fallbackFormat: MediaFormat): DecodedPcm {
        val output = ByteArrayOutputStream()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        val timeoutUs = 10_000L

        // The decoder reports the PCM format it actually produces via INFO_OUTPUT_FORMAT_CHANGED.
        // For Opus this is not necessarily the container's declared rate — Opus decodes at 48kHz
        // internally regardless of the source rate — so EXTRA_AUDIO_SOURCE_SAMPLING_RATE below
        // must use this, not fallbackFormat's. fallbackFormat only covers the (rare) case where
        // the format-changed event never fires.
        var sampleRate = fallbackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = fallbackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        while (!sawOutputEos) {
            if (!sawInputEos) {
                val inputIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channelCount = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
                MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (outputIndex >= 0) {
                    if (bufferInfo.size > 0) {
                        codec.getOutputBuffer(outputIndex)?.let { outputBuffer ->
                            val chunk = ByteArray(bufferInfo.size)
                            outputBuffer.get(chunk)
                            output.write(chunk)
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEos = true
                    }
                }
            }
        }
        return DecodedPcm(output.toByteArray(), sampleRate, channelCount)
    }

    /**
     * Feeds [pcm] to the on-device recognizer through a pipe instead of the microphone.
     * `RecognizerIntent.EXTRA_AUDIO_SOURCE` (API 33) takes a [ParcelFileDescriptor] of raw PCM;
     * anything else — including the file-path `String` this class used to pass — is silently
     * ignored, and an ignored source is exactly what made `startListening` fall back to the
     * live mic (issue #381).
     */
    private suspend fun recognizeFromPcm(pcm: DecodedPcm, languageCode: String): String? {
        // isAvailable() already gates the only caller (transcribe()) on API 33, but repeat the
        // check here too: it keeps this function safe to call on its own and keeps the API-level
        // guard next to the API 33 calls it protects.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null

        return coroutineScope {
            val pipe = try {
                ParcelFileDescriptor.createPipe()
            } catch (_: IOException) {
                return@coroutineScope null
            }
            val readSide = pipe[0]
            val writeSide = pipe[1]

            // Fill the pipe off the main thread while the recognizer reads it below on this
            // (main) thread — writing synchronously here would deadlock once the OS pipe buffer
            // fills, which anything past a few seconds of audio will do.
            val writer = launch(Dispatchers.IO) {
                ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { out ->
                    try {
                        out.write(pcm.bytes)
                    } catch (_: IOException) {
                        // The recognizer closed its end early (e.g. it ignored
                        // EXTRA_AUDIO_SOURCE) — onError below still resolves the coroutine.
                    }
                }
            }

            try {
                suspendCancellableCoroutine { continuation ->
                    try {
                        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                        continuation.invokeOnCancellation { recognizer.destroy() }

                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                            // The actual fix: a ParcelFileDescriptor of raw PCM, plus the format
                            // it's in, so the recognizer never has a reason to open the mic.
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, readSide)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, pcm.channelCount)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, pcm.sampleRate)
                        }

                        recognizer.setRecognitionListener(object : RecognitionListener {
                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                recognizer.destroy()
                                if (continuation.isActive) continuation.resume(matches?.firstOrNull())
                            }

                            override fun onError(error: Int) {
                                recognizer.destroy()
                                if (continuation.isActive) continuation.resume(null)
                            }

                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onPartialResults(partialResults: Bundle?) {}
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })

                        recognizer.startListening(intent)
                    } catch (_: Throwable) {
                        // Defensive: guards a linkage error (e.g. NoSuchMethodError) from an OEM
                        // image that advertises API 33 but doesn't fully implement on-device
                        // recognition — the exact failure mode issue #381 hit on API 26-30 via
                        // an unguarded call this class no longer makes.
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            } finally {
                writer.cancel()
                // Closing our copy unblocks a writer still stuck on a full pipe, and releases
                // the fd — "the caller of the recognizer is responsible for closing the audio"
                // per RecognizerIntent's own EXTRA_AUDIO_SOURCE documentation.
                try {
                    readSide.close()
                } catch (_: IOException) {
                    // Already closed.
                }
            }
        }
    }

    private data class DecodedPcm(val bytes: ByteArray, val sampleRate: Int, val channelCount: Int)
}
