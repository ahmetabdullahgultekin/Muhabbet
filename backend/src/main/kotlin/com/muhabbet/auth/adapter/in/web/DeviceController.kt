package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.port.`in`.RegisterPushTokenUseCase
import com.muhabbet.shared.dto.ApiResponse
import com.muhabbet.shared.dto.RegisterPushTokenRequest
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/devices")
class DeviceController(
    private val registerPushTokenUseCase: RegisterPushTokenUseCase
) {

    @PutMapping("/push-token")
    fun registerPushToken(@RequestBody request: RegisterPushTokenRequest): ResponseEntity<ApiResponse<Nothing>> {
        val userId = AuthenticatedUser.currentUserId()
        val deviceId = AuthenticatedUser.currentDeviceId()
        registerPushTokenUseCase.registerPushToken(userId, deviceId, request.pushToken)
        return ApiResponseBuilder.noContent()
    }
}
