package com.muhabbet.app.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #794: one clock format for every duration the app shows. Before this, the call timer padded
 * minutes ("01:05"), voice notes did not ("1:05"), and neither rolled over at an hour, so a long
 * call read "61:05".
 */
class FormatDurationTest {

    @Test
    fun should_padSecondsButNotMinutes_when_underAnHour() {
        assertEquals("0:00", DateTimeFormatter.formatDuration(0))
        assertEquals("0:07", DateTimeFormatter.formatDuration(7))
        assertEquals("1:05", DateTimeFormatter.formatDuration(65))
        assertEquals("59:59", DateTimeFormatter.formatDuration(3599))
    }

    @Test
    fun should_rollOverToHours_when_anHourOrMore() {
        assertEquals("1:00:00", DateTimeFormatter.formatDuration(3600))
        assertEquals("1:01:05", DateTimeFormatter.formatDuration(3665))
        assertEquals("10:00:01", DateTimeFormatter.formatDuration(36001))
    }

    @Test
    fun should_clampToZero_when_negative() {
        // A countdown computed as expiresAt - now overshoots by a tick; it must never read "-1:59".
        assertEquals("0:00", DateTimeFormatter.formatDuration(-1))
        assertEquals("0:00", DateTimeFormatter.formatDuration(-3700))
    }
}
