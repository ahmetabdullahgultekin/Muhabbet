package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.port.`in`.TwoStepVerificationUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SetupPinRequest(val pin: String, val email: String? = null)
data class VerifyPinRequest(val pin: String)
data class DisablePinRequest(val currentPin: String)
data class ResetPinRequest(val email: String)
data class TwoStepStatusResponse(val enabled: Boolean)

@RestController
@RequestMapping("/api/v1/auth/two-step")
class TwoStepVerificationController(
    private val twoStepVerificationUseCase: TwoStepVerificationUseCase
) {

    @PostMapping("/setup")
    fun setupPin(@RequestBody request: SetupPinRequest): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        twoStepVerificationUseCase.setupPin(userId, request.pin, request.email)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/verify")
    fun verifyPin(@RequestBody request: VerifyPinRequest): ResponseEntity<ApiResponse<TwoStepStatusResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val valid = twoStepVerificationUseCase.verifyPin(userId, request.pin)
        return ApiResponseBuilder.ok(TwoStepStatusResponse(enabled = valid))
    }

    @DeleteMapping
    fun disablePin(@RequestBody request: DisablePinRequest): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        twoStepVerificationUseCase.disablePin(userId, request.currentPin)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/reset")
    fun resetPinViaEmail(@RequestBody request: ResetPinRequest): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        twoStepVerificationUseCase.resetPinViaEmail(userId, request.email)
        return ApiResponseBuilder.ok(Unit)
    }
}
