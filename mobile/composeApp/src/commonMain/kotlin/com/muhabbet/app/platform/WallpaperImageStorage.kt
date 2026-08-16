package com.muhabbet.app.platform

import androidx.compose.runtime.Composable

/**
 * Persists a picked wallpaper image into app-private storage and returns an absolute path the app
 * can reopen later.
 *
 * [PickedImage.fileName] alone is a label, not a location on disk — storing just that (as the
 * CUSTOM wallpaper picker used to) left nothing for the chat screen to actually open (#380). This
 * writes the bytes once, at pick time, and hands back a path that `WallpaperRepository` can persist
 * and a `file://` URI can later resolve.
 */
expect class WallpaperImageSaver {
    /** Returns the absolute path the bytes were written to, or null if the write failed. */
    fun save(fileName: String, bytes: ByteArray): String?
}

@Composable
expect fun rememberWallpaperImageSaver(): WallpaperImageSaver
