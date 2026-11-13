package com.muhabbet.designsystem.image

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import coil3.request.crossfadeMillis
import coil3.request.ImageRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The loader the app installs (#847) actually carries the caches and the crossfade it claims to —
 * before it existed, the app ran on Coil's unconfigured implicit singleton.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MuhabbetImageLoaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `should configure a bounded memory cache when the loader is built`() {
        val loader = MuhabbetImageLoader.newImageLoader(context)
        val memory = requireNotNull(loader.memoryCache) { "memory cache must be configured" }
        assertTrue("memory cache must have room", memory.maxSize > 0)
    }

    @Test
    fun `should put the disk cache in its own folder under the app cache dir when the loader is built`() {
        val loader = MuhabbetImageLoader.newImageLoader(context)
        val disk = requireNotNull(loader.diskCache) { "disk cache must be configured" }
        assertEquals(MuhabbetImageLoader.DiskCacheMaxBytes, disk.maxSize)
        assertEquals(MuhabbetImageLoader.DiskCacheDirectoryName, disk.directory.name)
        assertEquals(context.cacheDir.absolutePath, disk.directory.parent.toString())
    }

    @Test
    fun `should crossfade by default when a request does not say otherwise`() {
        val loader = MuhabbetImageLoader.newImageLoader(context)
        val request = ImageRequest.Builder(context).data("x").defaults(loader.defaults).build()
        assertEquals(MuhabbetImageLoader.CrossfadeMillis, request.crossfadeMillis)
    }

    @Test
    fun `should apply the caller's configuration when one is passed`() {
        val loader = MuhabbetImageLoader.newImageLoader(context) { memoryCache(null) }
        assertEquals(null, loader.memoryCache)
    }
}
