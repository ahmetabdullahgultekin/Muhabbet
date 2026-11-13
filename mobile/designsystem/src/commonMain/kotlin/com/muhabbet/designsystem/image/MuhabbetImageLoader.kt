package com.muhabbet.designsystem.image

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.FileSystem

/**
 * The app's one image loader, installed at the root with `setSingletonImageLoaderFactory` (#838).
 *
 * Before this, nothing configured Coil at all, so every cache size and behaviour was whatever the
 * library defaulted to and nobody could say what the app was actually doing. The numbers:
 *
 *  - **Memory cache: [MemoryCachePercent] of the app's available memory.** Avatars are small and
 *    re-shown constantly — list, chat bar, profile — so a hit here is what keeps scrolling a contact
 *    list from re-decoding; 20% leaves the rest of the heap to the chat itself.
 *  - **Disk cache: [DiskCacheBytes] in the platform temporary/cache directory.** On Android that is
 *    the app's cache dir, which the OS may clear under storage pressure — the right home for bytes
 *    that can always be fetched again.
 *  - **No global crossfade.** [com.muhabbet.designsystem.components.UserAvatar] fades its own photo
 *    in on a MuhabbetMotion spring, honouring reduced motion; a loader-wide crossfade would double it.
 *
 * It deliberately does **not** reuse the app's authenticated Ktor client. That client attaches a
 * bearer token to every request, and media URLs are presigned S3 URLs: a second auth mechanism on a
 * presigned request is rejected by MinIO, and a session token has no business travelling to the
 * media host anyway. Coil's own network fetcher (registered by `coil-network-ktor3` in the app
 * module) builds an unauthenticated client of its own.
 */
fun muhabbetImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, MemoryCachePercent).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / DiskCacheDirectory)
                .maxSizeBytes(DiskCacheBytes)
                .build()
        }
        .crossfade(false)
        .build()

private const val MemoryCachePercent = 0.20
private const val DiskCacheBytes = 64L * 1024 * 1024
private const val DiskCacheDirectory = "muhabbet_image_cache"
