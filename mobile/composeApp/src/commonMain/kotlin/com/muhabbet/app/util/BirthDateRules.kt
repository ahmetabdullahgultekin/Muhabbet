package com.muhabbet.app.util

import com.muhabbet.shared.validation.ValidationRules
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.yearsUntil
import kotlin.time.Clock

/**
 * What the three date-of-birth fields currently hold (#751), judged before anything is sent.
 *
 * The server is the authority on age; this exists so the screen can disable its button for a half
 * typed date and name an impossible one without a round trip. The numbers come from
 * [ValidationRules], the same constants the backend reads.
 */
sealed interface BirthDateCheck {
    /** Not every field is filled yet. Not an error — the user is still typing. */
    data object Incomplete : BirthDateCheck

    /** Not a calendar date, in the future, or older than anyone alive. */
    data object Invalid : BirthDateCheck

    data class BelowMinimum(val date: LocalDate) : BirthDateCheck

    data class Valid(val date: LocalDate) : BirthDateCheck
}

/** Widths of the three date-of-birth fields; the screen caps its input with the same numbers. */
internal const val BIRTH_DAY_DIGITS = 2
internal const val BIRTH_MONTH_DIGITS = 2
internal const val BIRTH_YEAR_DIGITS = 4

/**
 * Judges a date of birth entered as separate day, month and year fields against [today].
 *
 * Fields are taken as typed — digits only is the screen's job, so anything else here is [BirthDateCheck.Invalid].
 */
fun checkBirthDate(day: String, month: String, year: String, today: LocalDate): BirthDateCheck {
    if (day.isEmpty() || month.isEmpty() || year.length < BIRTH_YEAR_DIGITS) return BirthDateCheck.Incomplete
    if (day.length > BIRTH_DAY_DIGITS || month.length > BIRTH_MONTH_DIGITS || year.length > BIRTH_YEAR_DIGITS) {
        return BirthDateCheck.Invalid
    }
    val date = parseDate(day.toIntOrNull(), month.toIntOrNull(), year.toIntOrNull())
        ?: return BirthDateCheck.Invalid
    if (date > today) return BirthDateCheck.Invalid

    val age = date.yearsUntil(today)
    return when {
        age > ValidationRules.MAX_PLAUSIBLE_AGE_YEARS -> BirthDateCheck.Invalid
        age < ValidationRules.MIN_AGE_YEARS -> BirthDateCheck.BelowMinimum(date)
        else -> BirthDateCheck.Valid(date)
    }
}

private fun parseDate(day: Int?, month: Int?, year: Int?): LocalDate? {
    if (day == null || month == null || year == null) return null
    // LocalDate's constructor is the calendar: it refuses 31 April and 29 February outside a leap year.
    return try {
        LocalDate(year, month, day)
    } catch (_: IllegalArgumentException) {
        null
    }
}

/**
 * Today where the age rule is applied. The server counts birthdays in Türkiye's calendar, so the
 * client does too; a device set to another zone would otherwise disagree with it around midnight.
 */
fun todayForAgeRule(): LocalDate = Clock.System.todayIn(TimeZone.of("Europe/Istanbul"))
