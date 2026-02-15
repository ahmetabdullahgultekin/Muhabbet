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
     * Format ISO string to context-aware timestamp:
     * - Today → "HH:mm"
     * - This year → "dd.MM"
     * - Other years → "dd.MM.yy"
     */
    fun formatConversationTimestamp(isoString: String): String {
        return try {
            val instant = Instant.parse(isoString)
            val tz = TimeZone.currentSystemDefault()
            val msgDate = instant.toLocalDateTime(tz)
            val nowDate = Clock.System.now().toLocalDateTime(tz)

            if (msgDate.date == nowDate.date) {
                formatTime(instant)
            } else if (msgDate.year == nowDate.year) {
                "${msgDate.dayOfMonth.toString().padStart(2, '0')}.${msgDate.monthNumber.toString().padStart(2, '0')}"
            } else {
                "${msgDate.dayOfMonth.toString().padStart(2, '0')}.${msgDate.monthNumber.toString().padStart(2, '0')}.${msgDate.year % 100}"
            }
        } catch (_: Exception) {
            ""
        }
    }

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

    /** Format seconds to "M:ss" for voice/call duration display */
    fun formatDuration(seconds: Int): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "$mins:${secs.toString().padStart(2, '0')}"
    }
}
