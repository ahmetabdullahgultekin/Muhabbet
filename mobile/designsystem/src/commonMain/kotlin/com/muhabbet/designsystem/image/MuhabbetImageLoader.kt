package com.muhabbet.designsystem.image

import androidx.compose.runtime.Composable
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import okio.Path

/**
 * The app's one image pipeline: memory cache, disk cache and a default crossfade, configured once.
 *
 * Until #847 nothing configured Coil at all. Every `AsyncImage` in the app ran on Coil's implicit
 * singleton, so the cache sizes were whatever the library defaulted to on each platform, nothing
 * crossfaded, and there was no single place to add the authenticated network fetcher, a size cap or
 * a debug logger later. This is that place; the app installs it once at the root with
 * [InstallMuhabbetImageLoader].
 *
 * The network layer is deliberately **not** chosen here — this module does not depend on Ktor (see
 * the module's build file). The app passes its fetcher through `configure`, e.g.
 * `components { add(KtorNetworkFetcherFactory(httpClient)) }`, so images go through the same
 * client, the same TLS policy and the same proxy as the API calls.
 */
object MuhabbetImageLoader {

    /** Share of the app's memory budget decoded bitmaps may hold. Coil's own default, made explicit. */
    const val MemoryCachePercent: Double = 0.25

    /** Cap on the on-disk cache. A chat app's media is large and re-fetchable; 256 MB is generous. */
    const val DiskCacheMaxBytes: Long = 256L * 1024 * 1024

    /** Subdirectory of the platform cache directory the disk cache owns. */
    const val DiskCacheDirectoryName: String = "muhabbet_image_cache"

    /**
     * Fade from nothing to the image, in ms, for call sites that still use a raw `AsyncImage`.
     * `MuhabbetImage` turns this off per request and runs its own fade over its own placeholder —
     * two fades stacked would multiply into a visibly slower one.
     */
    const val CrossfadeMillis: Int = 200

    /**
     * Builds the loader. Call through [InstallMuhabbetImageLoader] rather than holding one yourself:
     * a second loader means a second memory cache, and the same avatar decoded twice.
     */
    fun newImageLoader(
        context: PlatformContext,
        configure: ImageLoader.Builder.() -> Unit = {}
    ): ImageLoader = ImageLoader.Builder(context)
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, MemoryCachePercent).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(imageDiskCacheRoot(context) / DiskCacheDirectoryName)
                .maxSizeBytes(DiskCacheMaxBytes)
                .build()
        }
        .crossfade(CrossfadeMillis)
        .apply(configure)
        .build()
}

/**
 * Installs [MuhabbetImageLoader] as Coil's singleton. Call once, as high in the tree as the app's
 * root composable; Coil builds the loader lazily on the first image request.
 */
@Composable
fun InstallMuhabbetImageLoader(configure: ImageLoader.Builder.() -> Unit = {}) {
    setSingletonImageLoaderFactory { context -> MuhabbetImageLoader.newImageLoader(context, configure) }
}

/** The platform's cache directory: `Context.cacheDir` on Android, the temporary directory on iOS. */
internal expect fun imageDiskCacheRoot(context: PlatformContext): Path
