package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.muhabbet.app.util.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSHomeDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile

private const val TAG = "WallpaperImageSaver"

/**
 * Writes into the app's `Documents` directory (not `NSTemporaryDirectory`, which the OS is free to
 * purge under storage pressure) so a chosen wallpaper survives relaunches the same way it does on
 * Android's `filesDir`.
 */
actual class WallpaperImageSaver {

    @OptIn(ExperimentalForeignApi::class)
    actual fun save(fileName: String, bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return try {
            val path = NSHomeDirectory() + "/Documents/wallpaper_" + fileName
            val data = bytes.toNSData()
            if (data.writeToFile(path, atomically = true)) path else {
                Log.e(TAG, "writeToFile returned false for custom wallpaper image")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist custom wallpaper image", e)
            null
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    return this.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
    }
}

@Composable
actual fun rememberWallpaperImageSaver(): WallpaperImageSaver = remember { WallpaperImageSaver() }
