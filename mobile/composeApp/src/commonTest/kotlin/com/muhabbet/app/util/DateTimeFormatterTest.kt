package com.muhabbet.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * The conversation list's "when" column (#585).
 *
 * Everything here is relative to *now* rather than to a fixed instant, because
 * `formatConversationTimestamp` reads `Clock.System.now()` and there is no seam to inject a clock
 * through. That is a real limitation of the function and it is worth naming: these tests prove the
 * *boundaries between* the branches, which is where the bug was, and cannot prove behaviour at a
 * specific wall-clock date.
 *
 * Times are built at 12:00 local so that a test running near midnight cannot slide a case into the
 * neighbouring day — the one flake this suite could plausibly have.
 */
class DateTimeFormatterTest {

    private val tz = TimeZone.currentSystemDefault()

    private val labels = DateTimeFormatter.RelativeDayLabels(
        yesterday = "Dün",
        weekdays = listOf(
            "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"
        )
    )

    /** Midday, [daysAgo] calendar days back, as the ISO string the conversation list carries. */
    private fun isoDaysAgo(daysAgo: Int): String {
        val today = Clock.System.now().toLocalDateTime(tz).date
        val date = today.minus(daysAgo, DateTimeUnit.DAY)
        return date.atStartOfDayIn(tz).plus(kotlin.time.Duration.parse("12h")).toString()
    }

    @Test
    fun `today shows a clock time`() {
        val formatted = DateTimeFormatter.formatConversationTimestamp(isoDaysAgo(0), labels)
        assertEquals("12:00", formatted)
    }

    @Test
    fun `yesterday shows the word, not a date`() {
        val formatted = DateTimeFormatter.formatConversationTimestamp(isoDaysAgo(1), labels)
        assertEquals("Dün", formatted)
    }

    /**
     * The complaint that opened #585: yesterday rendered as `16.08`, which at row size is the same
     * shape as `16:08`. Whatever the day name is, it must not be a number-dot-number.
     */
    @Test
    fun `yesterday is never mistakable for a clock`() {
        val formatted = DateTimeFormatter.formatConversationTimestamp(isoDaysAgo(1), labels)
        assertTrue(formatted !in labels.weekdays, "yesterday should use its own word, not a weekday")
        assertTrue(!formatted.contains("."), "must not contain a dot: $formatted")
        assertTrue(!formatted.contains(":"), "must not contain a colon: $formatted")
    }

    @Test
    fun `two to six days ago shows a weekday name`() {
        for (daysAgo in 2..6) {
            val formatted = DateTimeFormatter.formatConversationTimestamp(isoDaysAgo(daysAgo), labels)
            assertTrue(
                formatted in labels.weekdays,
                "$daysAgo days ago should be a weekday name, was '$formatted'"
            )
        }
    }

    /** The weekday must be the *right* one — an off-by-one in the Monday-first list is invisible. */
    @Test
    fun `the weekday name matches the actual day`() {
        for (daysAgo in 2..6) {
            val iso = isoDaysAgo(daysAgo)
            val expected = labels.weekdays[
                kotlinx.datetime.Instant.parse(iso).toLocalDateTime(tz).date.dayOfWeek.ordinal
            ]
            assertEquals(
                expected,
                DateTimeFormatter.formatConversationTimestamp(iso, labels),
                "wrong weekday for $daysAgo days ago"
            )
        }
    }

    /**
     * Seven days is the boundary. It falls through to the numeric date, and that date carries the
     * **full year** — the two-digit form the old code used within the current year is what made
     * `16.08` collide with a time.
     */
    @Test
    fun `seven days ago falls back to a full numeric date`() {
        val formatted = DateTimeFormatter.formatConversationTimestamp(isoDaysAgo(7), labels)
        assertTrue(formatted !in labels.weekdays, "7 days is past the weekday window: $formatted")
        assertTrue(
            Regex("""^\d{2}\.\d{2}\.\d{4}$""").matches(formatted),
            "expected dd.MM.yyyy, was '$formatted'"
        )
    }

    @Test
    fun `a year ago shows a full numeric date`() {
        val formatted = DateTimeFormatter.formatConversationTimestamp(isoDaysAgo(365), labels)
        assertTrue(
            Regex("""^\d{2}\.\d{2}\.\d{4}$""").matches(formatted),
            "expected dd.MM.yyyy, was '$formatted'"
        )
    }

    /**
     * A conversation row must never crash or print a stack trace because a timestamp was malformed.
     * The empty string is the row rendering nothing, which is the right failure here.
     */
    @Test
    fun `an unparseable timestamp yields empty rather than throwing`() {
        assertEquals("", DateTimeFormatter.formatConversationTimestamp("", labels))
        assertEquals("", DateTimeFormatter.formatConversationTimestamp("not-a-date", labels))
    }

    /**
     * A short list is tolerated rather than fatal — see the note on [DateTimeFormatter.RelativeDayLabels].
     * A wrong label costs friendliness; an exception in a list row costs the screen.
     */
    @Test
    fun `a malformed weekday list degrades to the numeric date`() {
        val broken = DateTimeFormatter.RelativeDayLabels(yesterday = "Dün", weekdays = emptyList())
        val formatted = DateTimeFormatter.formatConversationTimestamp(isoDaysAgo(3), broken)
        assertTrue(
            Regex("""^\d{2}\.\d{2}\.\d{4}$""").matches(formatted),
            "expected a numeric fallback, was '$formatted'"
        )
    }
}
