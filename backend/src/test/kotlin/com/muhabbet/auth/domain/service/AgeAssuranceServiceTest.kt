package com.muhabbet.auth.domain.service

import com.muhabbet.auth.adapter.out.external.SelfDeclarationAssurance
import com.muhabbet.auth.domain.model.AgeAssuranceLevel
import com.muhabbet.auth.domain.model.User
import com.muhabbet.auth.domain.model.BirthDateDeclaration
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The declaration path for accounts that predate the age gate (#751): once, and a refusal sticks.
 */
class AgeAssuranceServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var service: AgeAssuranceService

    private val userId = TestData.USER_ID_1
    private val clock = Clock.fixed(Instant.parse("2026-11-12T09:00:00Z"), ZoneOffset.UTC)
    private val adult = LocalDate.of(1990, 5, 1)
    private val child = LocalDate.of(2015, 5, 1)

    private val undeclared = User(id = userId, phoneNumber = TestData.PHONE_1)

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        service = AgeAssuranceService(userRepository, SelfDeclarationAssurance(clock))
        every { userRepository.recordBirthDate(any(), any(), any(), any()) } returns true
    }

    @Test
    fun `should record a self-declared assurance when an existing account declares an adult date`() {
        every { userRepository.findById(userId) } returns undeclared

        val result = service.declareBirthDate(userId, adult)

        assertEquals(BirthDateDeclaration.Assured, result)
        verify { userRepository.recordBirthDate(userId, adult, AgeAssuranceLevel.SELF_DECLARED, any()) }
    }

    @Test
    fun `should record the date without an assurance when the declared age is below fifteen`() {
        every { userRepository.findById(userId) } returns undeclared

        val result = service.declareBirthDate(userId, child)

        assertEquals(BirthDateDeclaration.BelowMinimum, result)
        // Written so the refusal survives: otherwise "too young" would mean "try an earlier year".
        verify { userRepository.recordBirthDate(userId, child, null, null) }
    }

    @Test
    fun `should keep refusing when an under-age declaration is followed by an adult one`() {
        every { userRepository.findById(userId) } returns undeclared.copy(birthDate = child)

        val result = service.declareBirthDate(userId, adult)

        assertEquals(BirthDateDeclaration.BelowMinimum, result)
        verify(exactly = 0) { userRepository.recordBirthDate(any(), any(), any(), any()) }
    }

    @Test
    fun `should refuse with already set when an assured date is on record`() {
        every { userRepository.findById(userId) } returns undeclared.copy(
            birthDate = adult,
            ageAssuranceLevel = AgeAssuranceLevel.SELF_DECLARED,
            ageAssuredAt = Instant.now()
        )

        val thrown = assertThrows(BusinessException::class.java) {
            service.declareBirthDate(userId, LocalDate.of(1985, 1, 1))
        }

        assertEquals(ErrorCode.AUTH_BIRTH_DATE_ALREADY_SET, thrown.errorCode)
        verify(exactly = 0) { userRepository.recordBirthDate(any(), any(), any(), any()) }
    }

    @Test
    fun `should refuse with already set when a concurrent assured declaration wins the conditional write`() {
        every { userRepository.findById(userId) } returnsMany listOf(
            undeclared,
            undeclared.copy(
                birthDate = adult,
                ageAssuranceLevel = AgeAssuranceLevel.SELF_DECLARED,
                ageAssuredAt = Instant.now()
            )
        )
        every { userRepository.recordBirthDate(any(), any(), any(), any()) } returns false

        val thrown = assertThrows(BusinessException::class.java) {
            service.declareBirthDate(userId, adult)
        }

        assertEquals(ErrorCode.AUTH_BIRTH_DATE_ALREADY_SET, thrown.errorCode)
    }

    @Test
    fun `should answer below minimum when a concurrent under-age declaration wins the conditional write`() {
        // The loser declared an adult date, but the winner's under-age row is the one that stands.
        // ALREADY_SET here would read to the client as "already assured" and open the gate.
        every { userRepository.findById(userId) } returnsMany listOf(undeclared, undeclared.copy(birthDate = child))
        every { userRepository.recordBirthDate(any(), any(), any(), any()) } returns false

        val result = service.declareBirthDate(userId, adult)

        assertEquals(BirthDateDeclaration.BelowMinimum, result)
    }

    @Test
    fun `should refuse as invalid and record nothing when the date is in the future`() {
        every { userRepository.findById(userId) } returns undeclared

        val thrown = assertThrows(BusinessException::class.java) {
            service.declareBirthDate(userId, LocalDate.of(2030, 1, 1))
        }

        assertEquals(ErrorCode.AUTH_BIRTH_DATE_INVALID, thrown.errorCode)
        verify(exactly = 0) { userRepository.recordBirthDate(any(), any(), any(), any()) }
    }
}
