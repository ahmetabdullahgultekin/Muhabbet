package com.muhabbet.app.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.muhabbet.app.util.Log
import java.io.File
import java.io.IOException

private const val TAG = "WallpaperImageSaver"

actual class WallpaperImageSaver(private val context: Context) {
    actual fun save(fileName: String, bytes: ByteArray): String? {
        return try {
            val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val file = File(dir, fileName)
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Failed to persist custom wallpaper image", e)
            null
        }
    }
}

@Composable
actual fun rememberWallpaperImageSaver(): WallpaperImageSaver {
    val context = LocalContext.current
    return remember(context) { WallpaperImageSaver(context) }
}
