package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Input validation for the `birthDate` wire field (#751): an ISO-8601 calendar date or nothing.
 * Whether the date is a plausible or sufficient age is the domain's question, not this one's.
 */
internal fun parseBirthDate(raw: String?): LocalDate? {
    if (raw == null) return null
    return try {
        LocalDate.parse(raw.trim())
    } catch (e: DateTimeParseException) {
        throw BusinessException(ErrorCode.AUTH_BIRTH_DATE_INVALID)
    }
}
