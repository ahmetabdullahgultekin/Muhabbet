package com.muhabbet.designsystem.image

import coil3.PlatformContext
import okio.Path
import okio.Path.Companion.toOkioPath

/** The app's own cache directory, which the OS may clear under storage pressure — correct for images. */
internal actual fun imageDiskCacheRoot(context: PlatformContext): Path = context.cacheDir.toOkioPath()
