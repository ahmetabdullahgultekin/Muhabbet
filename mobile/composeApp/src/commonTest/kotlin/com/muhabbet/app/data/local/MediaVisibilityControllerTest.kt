package com.muhabbet.app.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Persistence and the reader for #593's setting — two of the three halves the standing rule asks
 * for. The third, the mechanism, is `MediaGallerySaver`, which is per-platform and cannot be
 * exercised from a JVM test; see the commit message for what that leaves unverified.
 */
class MediaVisibilityControllerTest {

    @Test
    fun should_default_to_off_on_a_device_that_never_chose() {
        // Opt-in on purpose: copying chat photos into an album every other app can read is not a
        // decision to make on someone's behalf, and below Android API 29 it needs a permission they
        // have not been asked for.
        assertFalse(MediaVisibilityController(FakeTokenStorage()).saveToGallery.value)
    }

    @Test
    fun should_persist_the_choice_so_the_next_launch_reads_it_back() {
        val storage = FakeTokenStorage()
        MediaVisibilityController(storage).setSaveToGallery(true)

        // A fresh controller over the same storage is what a relaunch looks like.
        assertTrue(MediaVisibilityController(storage).saveToGallery.value)
    }

    @Test
    fun should_publish_the_new_value_to_readers_that_are_already_collecting() {
        val controller = MediaVisibilityController(FakeTokenStorage())
        val seen = mutableListOf<Boolean>()
        seen += controller.saveToGallery.value
        controller.setSaveToGallery(true)
        seen += controller.saveToGallery.value
        controller.setSaveToGallery(false)
        seen += controller.saveToGallery.value

        assertEquals(listOf(false, true, false), seen)
    }
}
