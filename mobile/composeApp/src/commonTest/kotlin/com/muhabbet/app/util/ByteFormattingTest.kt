package com.muhabbet.app.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins [formatBytes] as it behaved while it was private to `SettingsSections`, because #546 gave it
 * a second caller and a move is exactly when a formatter quietly changes its rounding.
 */
class ByteFormattingTest {

    @Test
    fun should_print_bytes_without_a_unit_step_below_one_kilobyte() {
        assertEquals("0 B", formatBytes(0))
        assertEquals("1023 B", formatBytes(1023))
    }

    @Test
    fun should_step_to_binary_units_at_1024() {
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("1.0 MB", formatBytes(1024L * 1024))
        assertEquals("1.00 GB", formatBytes(1024L * 1024 * 1024))
    }

    /** Gigabytes carry two decimals; everything smaller carries one. */
    @Test
    fun should_round_to_the_places_each_unit_uses() {
        assertEquals("1.5 KB", formatBytes(1536))
        assertEquals("2.50 GB", formatBytes((2.5 * 1024 * 1024 * 1024).toLong()))
    }
}
