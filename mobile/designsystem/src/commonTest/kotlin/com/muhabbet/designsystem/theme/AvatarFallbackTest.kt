package com.muhabbet.designsystem.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** The avatar's identity colour (#838). */
class AvatarFallbackTest {

    @Test
    fun should_map_the_same_seed_to_the_same_pair_every_time() {
        val userId = "6f1c2a4e-1b7d-4c35-9a0e-3c2d1f0b9e88"
        assertEquals(avatarFallbackIndex(userId), avatarFallbackIndex(userId))
    }

    @Test
    fun should_keep_a_persons_colour_when_they_are_renamed() {
        // The seed is the id; the display name is not an input at all any more. Two renders of the
        // same user under two names hand the component the same seed, and get the same brush.
        val userId = "6f1c2a4e-1b7d-4c35-9a0e-3c2d1f0b9e88"
        assertSame(MuhabbetGradients.avatarFallback(userId), MuhabbetGradients.avatarFallback(userId))
    }

    @Test
    fun should_always_land_inside_the_pair_table() {
        listOf("", "?", "G", "İsmail", "🇹🇷", "x".repeat(10_000)).forEach { seed ->
            val index = avatarFallbackIndex(seed)
            assertTrue(index in 0 until AvatarFallbackPairCount, "seed '$seed' mapped to $index")
        }
    }

    @Test
    fun should_return_a_prebuilt_brush_rather_than_allocating_one_per_call() {
        // assertSame: the same instance, not merely an equal one. A new Brush per call was a new
        // allocation per avatar per recomposition, in every list row.
        val seed = "c0ffee"
        assertSame(MuhabbetGradients.avatarFallback(seed), MuhabbetGradients.avatarFallback(seed))
    }

    @Test
    fun should_spread_different_ids_across_more_than_one_colour() {
        val indices = (0 until 60).map { avatarFallbackIndex("user-$it") }.toSet()
        assertTrue(indices.size > 1, "60 distinct ids all mapped to one colour: $indices")
    }
}
