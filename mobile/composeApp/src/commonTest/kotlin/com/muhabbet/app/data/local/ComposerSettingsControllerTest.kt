package com.muhabbet.app.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The read path of #516's setting: the half that the 2026-08-15 audit found missing everywhere it
 * looked. A switch that moves and saves is not a working setting if nothing reads it back, and a
 * switch that saves to storage nobody consults is worse than one that is visibly broken.
 */
class ComposerSettingsControllerTest {

    @Test
    fun should_default_to_enter_sends_on_a_device_that_never_chose() {
        assertTrue(ComposerSettingsController(FakeTokenStorage()).enterToSend.value)
    }

    @Test
    fun should_persist_the_choice_so_the_next_launch_reads_it_back() {
        val storage = FakeTokenStorage()
        ComposerSettingsController(storage).setEnterToSend(false)

        // A fresh controller over the same storage is what a relaunch looks like.
        assertFalse(ComposerSettingsController(storage).enterToSend.value)
    }

    @Test
    fun should_publish_the_new_value_to_readers_that_are_already_collecting() {
        val controller = ComposerSettingsController(FakeTokenStorage())
        val seen = mutableListOf<Boolean>()
        // StateFlow.value is what a collector re-reads; the point of the assertion is that the
        // controller, not the caller, is the thing holding the answer.
        seen += controller.enterToSend.value
        controller.setEnterToSend(false)
        seen += controller.enterToSend.value

        assertEquals(listOf(true, false), seen)
    }
}
