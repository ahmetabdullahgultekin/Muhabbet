package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.model.BirthDateDeclaration
import com.muhabbet.auth.domain.port.`in`.DeclareBirthDateUseCase
import com.muhabbet.shared.dto.AgeStatusResponse
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.DeclareBirthDateRequest
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Where an account that predates the age gate declares its date of birth, once (#751).
 */
@RestController
@RequestMapping("/api/v1/users/me")
class BirthDateController(
    private val declareBirthDateUseCase: DeclareBirthDateUseCase
) {

    @PatchMapping("/birth-date")
    fun declareBirthDate(
        @RequestBody request: DeclareBirthDateRequest
    ): ResponseEntity<ApiResponse<AgeStatusResponse>> {
        val birthDate = parseBirthDate(request.birthDate)
            ?: throw BusinessException(ErrorCode.AUTH_BIRTH_DATE_INVALID)
        val userId = AuthenticatedUser.currentUserId()

        // The declaration has been recorded either way; below the minimum it is still a refusal.
        return when (declareBirthDateUseCase.declareBirthDate(userId, birthDate)) {
            BirthDateDeclaration.Assured ->
                ApiResponseBuilder.ok(AgeStatusResponse(ageVerificationRequired = false))
            BirthDateDeclaration.BelowMinimum ->
                throw BusinessException(ErrorCode.AUTH_AGE_BELOW_MINIMUM)
        }
    }
}
