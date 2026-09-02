package com.muhabbet.app.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.muhabbet.app.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "DeviceMediaCache"

/**
 * `context.cacheDir` — see the expect declaration for exactly what lives there and why nothing
 * outside it is touched.
 *
 * Both operations walk the tree on [Dispatchers.IO]: a cache with a few thousand thumbnails in it is
 * not something to stat on the frame thread, and the screen shows a spinner while this runs.
 */
actual class DeviceMediaCache(private val context: Context) {

    actual suspend fun sizeBytes(): Long = withContext(Dispatchers.IO) {
        runCatching { context.cacheDir.totalSize() }
            .onFailure { Log.e(TAG, "Failed to measure cache size", it) }
            .getOrDefault(0L)
    }

    actual suspend fun clear(): Long = withContext(Dispatchers.IO) {
        runCatching {
            val before = context.cacheDir.totalSize()
            // The directory itself stays; only its contents go. Deleting cacheDir outright works on
            // Android but leaves every holder of a File pointing at a path that no longer exists
            // until something recreates it.
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            before - context.cacheDir.totalSize()
        }.onFailure { Log.e(TAG, "Failed to clear cache", it) }.getOrDefault(0L)
    }
}

/**
 * Sums regular files only. `walkTopDown` also yields the directories, whose own `length()` is a
 * filesystem detail (typically 4 KB per directory on ext4) that would inflate the number the user
 * is being asked to act on.
 */
private fun File.totalSize(): Long =
    if (!exists()) 0L else walkTopDown().filter { it.isFile }.sumOf { it.length() }

@Composable
actual fun rememberDeviceMediaCache(): DeviceMediaCache {
    val context = LocalContext.current
    return remember(context) { DeviceMediaCache(context.applicationContext) }
}
