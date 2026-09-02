package com.muhabbet.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.muhabbet.app.util.Log
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private const val TAG = "DeviceMediaCache"

/**
 * `NSCachesDirectory` — the iOS counterpart of Android's `cacheDir`, and where Coil puts its disk
 * cache on this platform. The app's `Documents` directory, which holds the custom wallpaper, is a
 * different directory and is deliberately not walked; see the expect declaration.
 *
 * The OS may purge this directory on its own under storage pressure, which is a feature: it means
 * everything counted here is already understood by the system to be disposable.
 */
@OptIn(ExperimentalForeignApi::class)
actual class DeviceMediaCache {

    private val cachesPath: String?
        get() = NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String

    actual suspend fun sizeBytes(): Long = withContext(Dispatchers.Default) {
        val root = cachesPath ?: return@withContext 0L
        runCatching { totalSize(root) }
            .onFailure { Log.e(TAG, "Failed to measure cache size", it) }
            .getOrDefault(0L)
    }

    actual suspend fun clear(): Long = withContext(Dispatchers.Default) {
        val root = cachesPath ?: return@withContext 0L
        runCatching {
            val before = totalSize(root)
            val manager = NSFileManager.defaultManager
            @Suppress("UNCHECKED_CAST")
            val entries = manager.contentsOfDirectoryAtPath(root, null) as? List<String> ?: emptyList()
            // Each entry is removed on its own so one undeletable file — something the OS has open,
            // typically — does not abandon the rest. `error = null` because there is nothing useful
            // to do per file beyond continuing; the returned delta reports what actually went.
            entries.forEach { manager.removeItemAtPath("$root/$it", error = null) }
            before - totalSize(root)
        }.onFailure { Log.e(TAG, "Failed to clear cache", it) }.getOrDefault(0L)
    }

    /**
     * Sums regular files under [path]. `subpathsAtPath` walks the whole tree, and directories are
     * skipped by asking for their size and only counting entries that report one — the same choice
     * the Android actual makes, for the same reason.
     */
    private fun totalSize(path: String): Long {
        val manager = NSFileManager.defaultManager
        @Suppress("UNCHECKED_CAST")
        val subpaths = manager.subpathsAtPath(path) as? List<String> ?: return 0L
        return subpaths.sumOf { relative ->
            val attributes = manager.attributesOfItemAtPath("$path/$relative", null)
            (attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
        }
    }
}

@Composable
actual fun rememberDeviceMediaCache(): DeviceMediaCache = remember { DeviceMediaCache() }
