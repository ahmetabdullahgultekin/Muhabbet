package com.muhabbet.app.util

import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Centralized date/time formatting utility.
 * All screens should use these functions instead of duplicating formatting logic.
 */
object DateTimeFormatter {

    /** Format Instant to "HH:mm" */
    fun formatTime(instant: Instant): String {
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    }

    /** Format epoch millis to "HH:mm" */
    fun formatTime(epochMillis: Long): String =
        formatTime(Instant.fromEpochMilliseconds(epochMillis))

    /**
     * The localized words [formatConversationTimestamp] needs.
     *
     * Passed in rather than looked up, because this object is not Compose-aware and
     * `stringResource` is a `@Composable`. `formatDateSeparator` below already takes its labels the
     * same way; `rememberRelativeDayLabels()` is the composable that assembles this.
     *
     * @param weekdays seven names, **Monday first**, matching `DayOfWeek.ordinal`. A list of any
     *   other length is not rejected — the lookup falls back to the numeric date, so a mistake here
     *   costs a less friendly label rather than a crash in a list row.
     */
    data class RelativeDayLabels(
        val yesterday: String,
        val weekdays: List<String>
    )

    /**
     * The "when" column of the conversation list.
     *
     * | Age | Shows |
     * |---|---|
     * | today | `16:08` |
     * | yesterday | *Dün* |
     * | 2–6 days | weekday name |
     * | older | `16.08.2026` |
     *
     * Two complaints produced this shape (#585), and both are answered by it. `16.08` is not a
     * useful answer to "when" for something that happened yesterday — the day has a name and the
     * name is what people think in. And at list-row size, a dot between two two-digit numbers is
     * the same shape as a clock, so `16.08` and `16:08` were being read as each other. The old
     * format dropped the year within the current year, which is exactly when the collision happens;
     * the full year removes it, at the cost of a wider column on old conversations.
     */
    fun formatConversationTimestamp(isoString: String, labels: RelativeDayLabels): String {
        return try {
            val instant = Instant.parse(isoString)
            val tz = TimeZone.currentSystemDefault()
            val msgDate = instant.toLocalDateTime(tz)
            val nowDate = Clock.System.now().toLocalDateTime(tz)

            // Calendar days apart, not elapsed hours: 23:50 yesterday is "Dün" at 00:10 today, which
            // is what a reader means by it. `.toLong()` because kotlinx-datetime has returned both
            // Int and Long from toEpochDays() across versions.
            val daysAgo = (nowDate.date.toEpochDays() - msgDate.date.toEpochDays()).toLong()

            when {
                daysAgo == 0L -> formatTime(instant)
                daysAgo == 1L -> labels.yesterday
                daysAgo in 2L..6L ->
                    labels.weekdays.getOrNull(msgDate.date.dayOfWeek.ordinal) ?: numericDate(msgDate)
                else -> numericDate(msgDate)
            }
        } catch (_: Exception) {
            ""
        }
    }

    /** `dd.MM.yyyy`. The four-digit year is deliberate — see [formatConversationTimestamp]. */
    private fun numericDate(dt: kotlinx.datetime.LocalDateTime): String =
        "${dt.dayOfMonth.toString().padStart(2, '0')}." +
            "${dt.monthNumber.toString().padStart(2, '0')}.${dt.year}"

    /**
     * Format Instant to date separator label.
     * Pass localized todayLabel/yesterdayLabel from stringResource at the composable level.
     */
    fun formatDateSeparator(instant: Instant, todayLabel: String, yesterdayLabel: String): String {
        val tz = TimeZone.currentSystemDefault()
        val date = instant.toLocalDateTime(tz).date
        val now = Clock.System.now().toLocalDateTime(tz).date

        return when {
            date == now -> todayLabel
            date.toEpochDays() == now.toEpochDays() - 1 -> yesterdayLabel
            date.year == now.year ->
                "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}"
            else ->
                "${date.dayOfMonth.toString().padStart(2, '0')}.${date.monthNumber.toString().padStart(2, '0')}.${date.year}"
        }
    }

    /**
     * Format ISO string to last seen display:
     * - Today → "HH:mm"
     * - Other days → "dd.MM HH:mm"
     */
    fun formatLastSeen(isoString: String): String {
        return try {
            val instant = Instant.parse(isoString)
            val tz = TimeZone.currentSystemDefault()
            val dt = instant.toLocalDateTime(tz)
            val now = Clock.System.now().toLocalDateTime(tz)
            if (dt.date == now.date) {
                formatTime(instant)
            } else {
                "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')} " +
                    "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
            }
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Format ISO string to full timestamp: "dd.MM.yyyy HH:mm"
     */
    fun formatFullTimestamp(isoString: String): String {
        return try {
            val instant = Instant.parse(isoString)
            val tz = TimeZone.currentSystemDefault()
            val dt = instant.toLocalDateTime(tz)
            "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')}.${dt.year} " +
                "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
        } catch (_: Exception) {
            isoString
        }
    }

    /** Format epoch millis to full timestamp: "dd.MM.yyyy HH:mm" */
    fun formatFullTimestampMillis(epochMillis: Long): String {
        val tz = TimeZone.currentSystemDefault()
        val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
        return "${dt.dayOfMonth.toString().padStart(2, '0')}.${dt.monthNumber.toString().padStart(2, '0')}.${dt.year} " +
            "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }

    /** Format seconds to "M:ss" for voice/call duration display */
    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "$mins:${secs.toString().padStart(2, '0')}"
    }
}
