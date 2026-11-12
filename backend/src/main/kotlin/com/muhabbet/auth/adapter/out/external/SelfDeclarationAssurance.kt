package com.muhabbet.auth.adapter.out.external

import com.muhabbet.auth.domain.model.AgeAssuranceLevel
import com.muhabbet.auth.domain.model.AgeEvidence
import com.muhabbet.auth.domain.model.AgeVerdict
import com.muhabbet.auth.domain.port.out.AgeAssurancePort
import com.muhabbet.shared.validation.ValidationRules
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * The only [AgeAssurancePort] today: the user's own statement of their date of birth (#751).
 *
 * It establishes what the user *said*, nothing more. Whether that satisfies the statutory
 * age-verification duty is counsel question Q-L3, and nothing in the product may claim it does
 * until that is answered.
 *
 * "Today" is the calendar date in Türkiye, not the server's zone: a birthday turns at midnight where
 * the user lives, and the host runs in UTC, which would hold a fifteenth birthday back by three
 * hours.
 */
@Component
class SelfDeclarationAssurance(
    private val clock: Clock = Clock.system(AGE_ZONE)
) : AgeAssurancePort {

    override fun assess(evidence: AgeEvidence): AgeVerdict = when (evidence) {
        is AgeEvidence.SelfDeclaredBirthDate -> assessBirthDate(evidence.birthDate)
    }

    private fun assessBirthDate(birthDate: LocalDate): AgeVerdict {
        val today = LocalDate.now(clock.withZone(AGE_ZONE))
        if (birthDate.isAfter(today)) return AgeVerdict.Implausible
        val years = Period.between(birthDate, today).years
        return when {
            years > MAX_PLAUSIBLE_AGE_YEARS -> AgeVerdict.Implausible
            years < MIN_AGE_YEARS -> AgeVerdict.BelowMinimum
            else -> AgeVerdict.Assured(AgeAssuranceLevel.SELF_DECLARED)
        }
    }

    companion object {
        /** The statutory floor (Law 5651 Ek Md. 4 as amended by Law 7578). Stated once, in the shared module. */
        const val MIN_AGE_YEARS = ValidationRules.MIN_AGE_YEARS

        /** Older than anyone alive; a date beyond it is a typo, not an age. */
        const val MAX_PLAUSIBLE_AGE_YEARS = ValidationRules.MAX_PLAUSIBLE_AGE_YEARS

        val AGE_ZONE: ZoneId = ZoneId.of("Europe/Istanbul")
    }
}
