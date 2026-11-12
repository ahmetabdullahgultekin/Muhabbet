package com.muhabbet.app.util

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BirthDateRulesTest {

    private val today = LocalDate(2026, 11, 12)

    @Test
    fun should_be_valid_when_fifteenth_birthday_is_today() {
        assertEquals(BirthDateCheck.Valid(LocalDate(2011, 11, 12)), checkBirthDate("12", "11", "2011", today))
    }

    @Test
    fun should_be_below_minimum_when_fifteenth_birthday_is_tomorrow() {
        assertEquals(
            BirthDateCheck.BelowMinimum(LocalDate(2011, 11, 13)),
            checkBirthDate("13", "11", "2011", today)
        )
    }

    @Test
    fun should_accept_single_digit_day_and_month_when_typed_without_leading_zero() {
        assertEquals(BirthDateCheck.Valid(LocalDate(1990, 5, 1)), checkBirthDate("1", "5", "1990", today))
    }

    @Test
    fun should_be_incomplete_when_any_field_is_unfinished() {
        assertEquals(BirthDateCheck.Incomplete, checkBirthDate("", "5", "1990", today))
        assertEquals(BirthDateCheck.Incomplete, checkBirthDate("1", "", "1990", today))
        assertEquals(BirthDateCheck.Incomplete, checkBirthDate("1", "5", "199", today))
    }

    @Test
    fun should_be_invalid_when_the_calendar_has_no_such_date() {
        assertEquals(BirthDateCheck.Invalid, checkBirthDate("31", "04", "1990", today))
        assertEquals(BirthDateCheck.Invalid, checkBirthDate("29", "02", "1990", today))
        assertEquals(BirthDateCheck.Invalid, checkBirthDate("00", "01", "1990", today))
        assertEquals(BirthDateCheck.Invalid, checkBirthDate("01", "13", "1990", today))
    }

    @Test
    fun should_be_valid_when_the_date_is_a_leap_day_in_a_leap_year() {
        assertEquals(BirthDateCheck.Valid(LocalDate(2000, 2, 29)), checkBirthDate("29", "02", "2000", today))
    }

    @Test
    fun should_be_invalid_when_the_date_is_in_the_future() {
        assertEquals(BirthDateCheck.Invalid, checkBirthDate("13", "11", "2026", today))
    }

    @Test
    fun should_be_invalid_when_the_date_implies_an_impossible_age() {
        assertEquals(BirthDateCheck.Invalid, checkBirthDate("01", "01", "1890", today))
    }

    @Test
    fun should_be_invalid_when_a_field_holds_more_digits_than_it_allows() {
        assertEquals(BirthDateCheck.Invalid, checkBirthDate("012", "01", "1990", today))
        assertEquals(BirthDateCheck.Invalid, checkBirthDate("01", "01", "19900", today))
    }
}
