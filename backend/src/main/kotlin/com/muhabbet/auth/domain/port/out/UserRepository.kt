package com.muhabbet.auth.domain.port.out

import com.muhabbet.auth.domain.model.User
import java.util.UUID

interface UserRepository {
    fun findByPhoneNumber(phoneNumber: String): User?
    fun findById(id: UUID): User?
    fun findAllByIds(ids: List<UUID>): List<User>
    fun save(user: User): User
    fun existsByPhoneNumber(phoneNumber: String): Boolean
    fun updateLastSeenAt(userId: UUID, lastSeenAt: java.time.Instant)

    /**
     * Writes the age-gate columns (#751) only if no date of birth is on record yet, and reports
     * whether it did. Conditional in the statement itself, so the "declare once" rule holds under
     * concurrency and does not depend on a read made earlier.
     */
    fun recordBirthDate(
        userId: UUID,
        birthDate: java.time.LocalDate,
        level: com.muhabbet.auth.domain.model.AgeAssuranceLevel?,
        assuredAt: java.time.Instant?
    ): Boolean
}
