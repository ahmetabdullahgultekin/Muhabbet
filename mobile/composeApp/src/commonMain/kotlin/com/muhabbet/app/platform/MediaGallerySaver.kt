package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * What happened to one attempted gallery write.
 *
 * Four outcomes rather than a Boolean because the three failures need different answers from the
 * caller: [PERMISSION_REQUIRED] is fixable by asking, [UNSUPPORTED] never is, and [FAILED] is worth
 * a log line and nothing else. Collapsing them would leave the auto-saver retrying a permission it
 * can never obtain, once per received photo, forever.
 */
enum class GallerySaveResult {
    /** The bytes are in the device's shared media store and the photo app can see them. */
    SAVED,

    /**
     * The OS wants a permission this app has not been granted. Nothing was written.
     *
     * Only reachable on Android API ≤ 28 (see the Android actual) and on iOS before the add-only
     * Photos authorisation has been given.
     */
    PERMISSION_REQUIRED,

    /** This platform/build cannot write to a shared gallery at all — see [MediaGallerySaver.isSupported]. */
    UNSUPPORTED,

    /** The write was attempted and did not complete. Nothing partial is left behind. */
    FAILED
}

/**
 * Writes a received photo or video into the device's **shared** media store, so it shows up in the
 * phone's own photo app (#593).
 *
 * This is deliberately not [DeviceMediaCache]'s territory and not the app's private storage. The
 * whole point of the "media visibility" setting is that the file leaves the app's sandbox and
 * becomes an ordinary photo on the phone — which is also why it is opt-in, why it is a permission-
 * bearing operation on older Android, and why the two platforms need entirely different code:
 *
 * - **Android**: `MediaStore` insert under `Pictures/Muhabbet` (or `Movies/Muhabbet`). On API 29+
 *   scoped storage makes this permissionless; on 26–28 it is a legacy file write plus a media-scan
 *   broadcast and needs `WRITE_EXTERNAL_STORAGE`.
 * - **iOS**: `PHPhotoLibrary` with the **add-only** authorisation, which is a different prompt from
 *   the picker's read authorisation and needs `NSPhotoLibraryAddUsageDescription` in the host app's
 *   `Info.plist`. Requesting it without that key does not fail — it terminates the process — which
 *   is why [isSupported] checks for the key rather than assuming it.
 *
 * Nothing here decides *whether* to save; that is [com.muhabbet.app.data.repository.ReceivedMediaAutoSaver],
 * which owns the exclusions (view-once, disappearing, own messages).
 */
expect class MediaGallerySaver {
    /**
     * Whether a gallery write can succeed on this device at all.
     *
     * Callers must hide the setting rather than offer a switch that writes to nothing — the exact
     * failure #377/#378/#380 were: a control that moves and changes nothing is worse than an absent
     * one, because it tells the user the feature exists.
     */
    fun isSupported(): Boolean

    /**
     * Write [bytes] into the shared gallery under the app's own album.
     *
     * @param fileName the display name to store it under, extension included. Derived from the
     *   message id by the caller so a second save of the same message is recognisable rather than
     *   producing "photo (2)".
     * @param mimeType e.g. `image/jpeg`, `video/mp4`. Decides which collection the file lands in.
     */
    suspend fun save(bytes: ByteArray, fileName: String, mimeType: String): GallerySaveResult
}

@Composable
expect fun rememberMediaGallerySaver(): MediaGallerySaver

/**
 * Asks for whatever permission [MediaGallerySaver.save] needs, and reports whether it was given.
 *
 * Called when the user switches media visibility **on**, not when the first photo arrives: a
 * permission dialog that appears out of nowhere while someone is reading a chat is worse than one
 * that follows the switch they just flipped. Mirrors [rememberAudioPermissionRequester].
 *
 * On Android API 29+ there is nothing to ask for and the callback fires `true` immediately.
 */
@Composable
expect fun rememberGallerySavePermissionRequester(onResult: (Boolean) -> Unit): () -> Unit
