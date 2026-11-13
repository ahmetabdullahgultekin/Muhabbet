package com.muhabbet.designsystem.image

import coil3.PlatformContext
import okio.FileSystem
import okio.Path

/** The system temporary directory (`NSTemporaryDirectory()`), which iOS may purge — correct for images. */
internal actual fun imageDiskCacheRoot(context: PlatformContext): Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY
