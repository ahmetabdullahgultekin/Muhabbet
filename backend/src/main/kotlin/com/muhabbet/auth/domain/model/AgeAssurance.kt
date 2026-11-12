package com.muhabbet.auth.domain.model

import java.time.Instant
import java.time.LocalDate

/**
 * How an account's age was established (ADR 0029 rule 1). Only [SELF_DECLARED] is produced today;
 * the other two name the adapters counsel may require (Q-L3), so a verdict can already say which
 * evidence it would accept.
 */
enum class AgeAssuranceLevel { SELF_DECLARED, EDEVLET, NFC_ID }

/** What a caller offers as proof of age. */
sealed interface AgeEvidence {
    data class SelfDeclaredBirthDate(val birthDate: LocalDate) : AgeEvidence
}

/** The outcome of assessing [AgeEvidence] — a result, not an exception, so each caller decides what refusal means. */
sealed interface AgeVerdict {
    data class Assured(val level: AgeAssuranceLevel) : AgeVerdict
    data object BelowMinimum : AgeVerdict
    data class NeedsStrongerEvidence(val accepted: Set<AgeAssuranceLevel>) : AgeVerdict

    /** Not a plausible age at all: a birth date in the future, or one no living person could have. */
    data object Implausible : AgeVerdict
}

/** The three `users` columns that are written together once an age is assured. */
data class AssuredAge(
    val birthDate: LocalDate,
    val level: AgeAssuranceLevel,
    val assuredAt: Instant
)

/**
 * What a declaration amounted to. A result rather than an exception for [BelowMinimum], because that
 * declaration *is* recorded — it must survive the refusal, or the user could simply try another year.
 */
sealed interface BirthDateDeclaration {
    data object Assured : BirthDateDeclaration

    /** Recorded, and the account's age stays unassured. Also the answer for every later attempt. */
    data object BelowMinimum : BirthDateDeclaration
}
