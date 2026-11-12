package com.muhabbet.auth.domain.port.`in`

import com.muhabbet.auth.domain.model.BirthDateDeclaration
import java.time.LocalDate
import java.util.UUID

/**
 * Lets an account that predates the age gate declare a date of birth, once (#751).
 *
 * New accounts declare it at sign-in instead (`VerifyOtpUseCase`, `FirebaseVerifyUseCase`). An
 * existing account is not locked out for lacking one; it is asked on its next sign-in and answers
 * here.
 *
 * Throws `AUTH_BIRTH_DATE_ALREADY_SET` when an assured date is already on record — a declaration
 * cannot be replaced — and `AUTH_BIRTH_DATE_INVALID` for a date that is not a plausible age.
 */
interface DeclareBirthDateUseCase {
    fun declareBirthDate(userId: UUID, birthDate: LocalDate): BirthDateDeclaration
}
