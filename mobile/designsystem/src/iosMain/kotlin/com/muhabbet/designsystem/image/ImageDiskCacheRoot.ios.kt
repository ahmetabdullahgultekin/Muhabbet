package com.muhabbet.designsystem.image

import coil3.PlatformContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * The app's `Library/Caches` directory — iOS's home for re-creatable data. It survives normal use and
 * is purged only under storage pressure, unlike `NSTemporaryDirectory()`, which the system empties
 * aggressively and would have made the disk cache mostly miss. Falls back to the temporary
 * directory only if the lookup returns nothing, which it does not on a real device.
 */
internal actual fun imageDiskCacheRoot(context: PlatformContext): Path =
    (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).firstOrNull() as? String)
        ?.toPath()
        ?: FileSystem.SYSTEM_TEMPORARY_DIRECTORY
