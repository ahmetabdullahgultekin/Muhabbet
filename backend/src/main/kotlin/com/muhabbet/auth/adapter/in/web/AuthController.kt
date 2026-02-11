package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.port.`in`.LogoutUseCase
import com.muhabbet.auth.domain.port.`in`.RefreshTokenUseCase
import com.muhabbet.auth.domain.port.`in`.RequestOtpUseCase
import com.muhabbet.auth.domain.port.`in`.VerifyOtpUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.AuthTokenResponse
import com.muhabbet.shared.dto.RefreshTokenRequest
import com.muhabbet.shared.dto.RequestOtpRequest
import com.muhabbet.shared.dto.RequestOtpResponse
import com.muhabbet.shared.dto.VerifyOtpRequest
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import kotlinx.coroutines.runBlocking
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val requestOtpUseCase: RequestOtpUseCase,
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val refreshTokenUseCase: RefreshTokenUseCase,
    private val logoutUseCase: LogoutUseCase
) {

    @PostMapping("/otp/request")
    fun requestOtp(@RequestBody request: RequestOtpRequest): ResponseEntity<ApiResponse<RequestOtpResponse>> {
        val result = runBlocking { requestOtpUseCase.requestOtp(request.phoneNumber) }
        return ApiResponseBuilder.ok(
            RequestOtpResponse(
                ttlSeconds = result.ttlSeconds,
                retryAfterSeconds = result.retryAfterSeconds
            )
        )
    }

    @PostMapping("/otp/verify")
    fun verifyOtp(@RequestBody request: VerifyOtpRequest): ResponseEntity<ApiResponse<AuthTokenResponse>> {
        val result = runBlocking {
            verifyOtpUseCase.verifyOtp(
                phoneNumber = request.phoneNumber,
                otp = request.otp,
                deviceName = request.deviceName,
                platform = request.platform
            )
        }
        return ApiResponseBuilder.ok(
            AuthTokenResponse(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
                expiresIn = result.expiresIn,
                userId = result.userId,
                deviceId = result.deviceId,
                isNewUser = result.isNewUser
            )
        )
    }

    @PostMapping("/token/refresh")
    fun refreshToken(@RequestBody request: RefreshTokenRequest): ResponseEntity<ApiResponse<AuthTokenResponse>> {
        val result = runBlocking { refreshTokenUseCase.refresh(request.refreshToken) }
        return ApiResponseBuilder.ok(
            AuthTokenResponse(
                accessToken = result.accessToken,
                refreshToken = result.refreshToken,
                expiresIn = result.expiresIn,
                userId = "",
                deviceId = "",
                isNewUser = false
            )
        )
    }

    @PostMapping("/logout")
    fun logout(): ResponseEntity<ApiResponse<Nothing>> {
        val userId = AuthenticatedUser.currentUserId()
        val deviceId = AuthenticatedUser.currentDeviceId()
        runBlocking { logoutUseCase.logout(userId, deviceId) }
        return ApiResponseBuilder.noContent()
    }
}
