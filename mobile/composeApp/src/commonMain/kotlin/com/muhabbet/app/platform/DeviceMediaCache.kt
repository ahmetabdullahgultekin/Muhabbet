package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * The app's own cache directory on this device: how big it is, and a way to empty it.
 *
 * This is the half of "storage" that is actually **on the phone**, and #546 turns on the distinction
 * between it and the server figures the Settings card already showed. `GET /api/v1/media/storage`
 * sums `media_files` rows the user *uploaded* — bytes that live in MinIO. Deleting those, even if
 * the app could, would free nothing locally and would remove media from conversations other people
 * are still reading. What fills a phone is the copies the app downloaded, and those are here.
 *
 * Concretely, on Android this is `context.cacheDir`, which holds:
 *  - Coil's disk cache (`image_cache/`) — every avatar, thumbnail and photo the app has rendered;
 *  - `camera_*.jpg` written by [CameraPicker] before upload;
 *  - `voice_*.ogg` written by [AudioRecorder] before upload;
 *  - `transcribe_*.ogg` written by the transcriber.
 *
 * The last three are never cleaned up by the code that writes them, so on a chatty device they are
 * a slow leak this is the only way to stop.
 *
 * What it deliberately does **not** touch: `filesDir` (custom wallpapers — a choice the user made,
 * not a cache), the SQLDelight database (messages and the pending-send queue), and the encrypted
 * preference stores. Nothing here loses a message or a setting; everything here can be fetched
 * again. That is the whole reason this is the action the screen offers and server-side deletion is
 * not (see `StorageUsageScreen`).
 *
 * One known rough edge, small enough not to design around: clearing while a voice message is being
 * recorded or a photo is mid-upload deletes that temp file. Both live in a chat screen, which is not
 * on screen while Settings is, so it takes a background upload to hit — and the upload fails
 * visibly rather than sending the wrong bytes.
 */
expect class DeviceMediaCache {
    /** Total bytes under the cache directory. 0 on a device that has cached nothing, or on error. */
    suspend fun sizeBytes(): Long

    /** Empties the cache directory and returns how many bytes it held before. */
    suspend fun clear(): Long
}

@Composable
expect fun rememberDeviceMediaCache(): DeviceMediaCache
