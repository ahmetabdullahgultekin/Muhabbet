package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.model.AgeAssuranceLevel
import com.muhabbet.auth.domain.model.AgeEvidence
import com.muhabbet.auth.domain.model.AgeVerdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SelfDeclarationAssuranceTest {

    private fun assuranceAt(instant: String) =
        SelfDeclarationAssurance(Clock.fixed(Instant.parse(instant), ZoneOffset.UTC))

    private fun SelfDeclarationAssurance.of(birthDate: LocalDate) =
        assess(AgeEvidence.SelfDeclaredBirthDate(birthDate))

    @Test
    fun `should state the minimum age as fifteen when read from the companion`() {
        assertEquals(15, SelfDeclarationAssurance.MIN_AGE_YEARS)
    }

    @Test
    fun `should assure when the fifteenth birthday is today`() {
        val verdict = assuranceAt("2026-11-12T09:00:00Z").of(LocalDate.of(2011, 11, 12))
        assertEquals(AgeVerdict.Assured(AgeAssuranceLevel.SELF_DECLARED), verdict)
    }

    @Test
    fun `should refuse as below minimum when the fifteenth birthday is tomorrow`() {
        val verdict = assuranceAt("2026-11-12T09:00:00Z").of(LocalDate.of(2011, 11, 13))
        assertEquals(AgeVerdict.BelowMinimum, verdict)
    }

    @Test
    fun `should count the birthday in Istanbul time when the UTC date is still the day before`() {
        // 22:30 UTC on the 11th is 01:30 on the 12th in Istanbul: the birthday has arrived there.
        val verdict = assuranceAt("2026-11-11T22:30:00Z").of(LocalDate.of(2011, 11, 12))
        assertEquals(AgeVerdict.Assured(AgeAssuranceLevel.SELF_DECLARED), verdict)
    }

    @Test
    fun `should treat the date as implausible when it is in the future`() {
        val verdict = assuranceAt("2026-11-12T09:00:00Z").of(LocalDate.of(2026, 11, 13))
        assertEquals(AgeVerdict.Implausible, verdict)
    }

    @Test
    fun `should treat the date as implausible when it implies an age no one has`() {
        val verdict = assuranceAt("2026-11-12T09:00:00Z").of(LocalDate.of(1890, 1, 1))
        assertEquals(AgeVerdict.Implausible, verdict)
    }
}
