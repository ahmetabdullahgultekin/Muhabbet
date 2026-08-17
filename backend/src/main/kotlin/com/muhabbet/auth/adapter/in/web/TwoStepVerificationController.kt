package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.port.`in`.TwoStepVerificationUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.DisableTwoStepRequest
import com.muhabbet.shared.dto.SetupTwoStepRequest
import com.muhabbet.shared.dto.TwoStepStatusResponse
import com.muhabbet.shared.dto.VerifyTwoStepRequest
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class ResetPinRequest(val email: String)

/** Must match `TwoStepRepository.BASE_PATH` on the mobile client. */
const val TWO_STEP_BASE_PATH = "/api/v1/auth/two-step"

/**
 * The four addresses the settings screen uses, and the shapes it sends.
 *
 * The request and response types are the **shared** DTOs, which both this controller and the mobile
 * `TwoStepRepository` compile against. They used to be private copies declared in this file, and the
 * client's copy and this one had drifted apart in every way that matters (#544): the setup body went
 * to a path that only answered `DELETE`, and the disable call carried no body at all where this
 * signature requires one.
 */
@RestController
@RequestMapping(TWO_STEP_BASE_PATH)
class TwoStepVerificationController(
    private val twoStepVerificationUseCase: TwoStepVerificationUseCase
) {

    /**
     * The settings screen calls this the moment it opens, to show whether two-step is on. It had no
     * mapping, so the request fell through to static-resource handling and came back as a 500 —
     * making the whole feature look broken from the first tap.
     */
    @GetMapping("/status")
    fun status(): ResponseEntity<ApiResponse<TwoStepStatusResponse>> {
        val status = twoStepVerificationUseCase.status(AuthenticatedUser.currentUserId())
        return ApiResponseBuilder.ok(
            TwoStepStatusResponse(enabled = status.enabled, hasEmail = status.hasRecoveryEmail)
        )
    }

    @PostMapping("/setup")
    fun setupPin(@RequestBody request: SetupTwoStepRequest): ResponseEntity<ApiResponse<Unit>> {
        val userId = AuthenticatedUser.currentUserId()
        twoStepVerificationUseCase.setupPin(userId, request.pin, request.email)
        return ApiResponseBuilder.ok(Unit)
    }

    @PostMapping("/verify")
    fun verifyPin(@RequestBody request: VerifyTwoStepRequest): ResponseEntity<ApiResponse<TwoStepStatusResponse>> {
        val userId = AuthenticatedUser.currentUserId()
        val valid = twoStepVerificationUseCase.verifyPin(userId, request.pin)
        return ApiResponseBuilder.ok(TwoStepStatusResponse(enabled = valid))
    }

    /**
     * `POST /disable` rather than `DELETE` on the resource, even though deleting the resource is
     * what it does: turning two-step off requires the current PIN, and a request body on `DELETE` is
     * the one shape HTTP intermediaries are entitled to drop. Traffic reaches this app through
     * Traefik, and a proxy that quietly strips the body would surface here as
     * `Required request body is missing` — indistinguishable, from the phone, from the 400 this
     * endpoint already answered for a year (#544).
     */
    @PostMapping("/disable")
    fun disablePin(@RequestBody request: DisableTwoStepRequest): ResponseEntity<ApiResponse<Unit>> {
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
