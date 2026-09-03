package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.muhabbet.app.util.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAssetResourceTypeVideo
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume

private const val TAG = "MediaGallerySaver"

/**
 * The `Info.plist` key iOS requires before the add-only Photos authorisation may even be requested.
 *
 * Its absence is not a permission denial — `requestAuthorizationForAccessLevel` **terminates the
 * process** when the usage description is missing. This repository contains no Xcode host app, so
 * nothing here can add the key; [MediaGallerySaver.isSupported] therefore reads it back at runtime
 * and reports the feature unavailable when a host app has not declared it. The Settings row is
 * hidden in that case rather than offering a switch whose first use would crash the app.
 */
private const val ADD_USAGE_DESCRIPTION_KEY = "NSPhotoLibraryAddUsageDescription"

/**
 * `PHPhotoLibrary` with the **add-only** access level — the narrowest thing that can put a photo in
 * the camera roll, and deliberately not the read authorisation `ImagePicker` uses. Add-only cannot
 * enumerate or read the user's library, which is the whole point: auto-save needs to write one file,
 * not to see the album.
 *
 * Not device-verified. There is no iOS host app in this repository and no Apple hardware on the
 * build host, so what is proven here is that it compiles for `iosArm64`/`iosSimulatorArm64` — not
 * that a photo appears in Photos. See CLAUDE.md's standing rule about what "done" means.
 */
@OptIn(ExperimentalForeignApi::class)
actual class MediaGallerySaver {

    actual fun isSupported(): Boolean =
        NSBundle.mainBundle.objectForInfoDictionaryKey(ADD_USAGE_DESCRIPTION_KEY) != null

    actual suspend fun save(
        bytes: ByteArray,
        fileName: String,
        mimeType: String
    ): GallerySaveResult {
        if (!isSupported()) return GallerySaveResult.UNSUPPORTED

        val status = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)
        if (status != PHAuthorizationStatusAuthorized && status != PHAuthorizationStatusLimited) {
            // Deliberately not requested here. The prompt belongs to the moment the user turns the
            // setting on (see rememberGallerySavePermissionRequester); raising it from a background
            // save would put a system dialog on screen because someone sent a photo.
            return GallerySaveResult.PERMISSION_REQUIRED
        }

        val data = bytes.toNSData() ?: return GallerySaveResult.FAILED
        val resourceType =
            if (mimeType.startsWith("video/")) PHAssetResourceTypeVideo else PHAssetResourceTypePhoto

        return suspendCancellableCoroutine { continuation ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges(
                changeBlock = {
                    PHAssetCreationRequest.creationRequestForAsset()
                        .addResourceWithType(resourceType, data = data, options = null)
                },
                completionHandler = { success, error ->
                    if (!success) Log.e(TAG, "Photos write failed for $fileName: ${error?.localizedDescription}")
                    if (continuation.isActive) {
                        continuation.resume(
                            if (success) GallerySaveResult.SAVED else GallerySaveResult.FAILED
                        )
                    }
                }
            )
        }
    }
}

/**
 * Empty input is rejected rather than turned into a zero-byte asset: `NSData.create` over a pinned
 * empty array has no valid address to take, and an empty photo in the camera roll is not a useful
 * outcome anyway.
 */
@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData? {
    if (isEmpty()) return null
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}

@Composable
actual fun rememberMediaGallerySaver(): MediaGallerySaver = remember { MediaGallerySaver() }

@Composable
actual fun rememberGallerySavePermissionRequester(onResult: (Boolean) -> Unit): () -> Unit = {
    if (NSBundle.mainBundle.objectForInfoDictionaryKey(ADD_USAGE_DESCRIPTION_KEY) == null) {
        // Requesting without the usage description crashes the app; refusing is the honest answer.
        onResult(false)
    } else {
        PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { status ->
            onResult(
                status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
            )
        }
    }
}
