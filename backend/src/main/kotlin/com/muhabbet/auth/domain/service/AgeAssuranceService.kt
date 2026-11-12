package com.muhabbet.auth.domain.service

import com.muhabbet.auth.domain.model.AgeEvidence
import com.muhabbet.auth.domain.model.AgeVerdict
import com.muhabbet.auth.domain.model.AssuredAge
import com.muhabbet.auth.domain.model.BirthDateDeclaration
import com.muhabbet.auth.domain.port.`in`.DeclareBirthDateUseCase
import com.muhabbet.auth.domain.port.out.AgeAssurancePort
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The age gate for accounts that existed before it (#751, ADR 0029 rule 1).
 *
 * A declaration is written once. Below the minimum age it is still written — with no assurance
 * level — so the refusal sticks: without that, "too young" would only mean "try an earlier year".
 * What happens to such an account beyond being refused here (ADR 0029 rule 5: revoke sessions) is
 * not decided by this service.
 */
open class AgeAssuranceService(
    private val userRepository: UserRepository,
    private val ageAssurancePort: AgeAssurancePort
) : DeclareBirthDateUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun declareBirthDate(userId: UUID, birthDate: LocalDate): BirthDateDeclaration {
        val user = userRepository.findById(userId)
            ?: throw BusinessException(ErrorCode.AUTH_UNAUTHORIZED)

        if (user.birthDate != null) {
            // The earlier declaration stands, whatever this one says.
            if (user.ageVerificationRequired) return BirthDateDeclaration.BelowMinimum
            throw BusinessException(ErrorCode.AUTH_BIRTH_DATE_ALREADY_SET)
        }

        val verdict = ageAssurancePort.assess(AgeEvidence.SelfDeclaredBirthDate(birthDate))
        val (assured, result) = when (verdict) {
            is AgeVerdict.Assured ->
                AssuredAge(birthDate, verdict.level, Instant.now()) to BirthDateDeclaration.Assured
            AgeVerdict.BelowMinimum -> null to BirthDateDeclaration.BelowMinimum
            AgeVerdict.Implausible -> throw BusinessException(ErrorCode.AUTH_BIRTH_DATE_INVALID)
            is AgeVerdict.NeedsStrongerEvidence -> throw BusinessException(ErrorCode.AUTH_AGE_REQUIRED)
        }

        // Conditional on the column still being empty, so two concurrent declarations cannot both win.
        val recorded = userRepository.recordBirthDate(
            userId = userId,
            birthDate = birthDate,
            level = assured?.level,
            assuredAt = assured?.assuredAt
        )
        if (!recorded) return lostRace(userId)

        log.info("Birth date declared: userId={}, assured={}", userId, assured != null)
        return result
    }

    /**
     * A concurrent declaration got there first, and it is the one that stands. Answer with *its*
     * outcome: reporting `ALREADY_SET` after an under-age winner would read to the client as
     * "already assured" and let the device past the gate.
     */
    private fun lostRace(userId: UUID): BirthDateDeclaration {
        val winner = userRepository.findById(userId)
        if (winner == null || winner.ageVerificationRequired) return BirthDateDeclaration.BelowMinimum
        throw BusinessException(ErrorCode.AUTH_BIRTH_DATE_ALREADY_SET)
    }
}
