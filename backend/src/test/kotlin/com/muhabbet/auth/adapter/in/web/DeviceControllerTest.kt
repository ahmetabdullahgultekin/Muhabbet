package com.muhabbet.auth.adapter.`in`.web

import com.muhabbet.auth.domain.port.`in`.RegisterPushTokenUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class DeviceControllerTest {

    private lateinit var registerPushTokenUseCase: RegisterPushTokenUseCase
    private lateinit var controller: DeviceController

    private val userId = TestData.USER_ID_1
    private val deviceId = TestData.DEVICE_ID_1

    @BeforeEach
    fun setUp() {
        registerPushTokenUseCase = mockk()
        controller = DeviceController(registerPushTokenUseCase)
        setAuthenticatedUser(userId, deviceId)
    }

    @Nested
    inner class RegisterPushToken {

        @Test
        fun `should register FCM push token`() {
            val token = "fcm-token-abc123xyz"
            every { registerPushTokenUseCase.registerPushToken(userId, deviceId, token) } returns Unit

            val response = controller.registerPushToken(
                com.muhabbet.shared.dto.RegisterPushTokenRequest(pushToken = token)
            )

            assert(response.statusCode.value() == 200)
            verify { registerPushTokenUseCase.registerPushToken(userId, deviceId, token) }
        }

        @Test
        fun `should handle token update for same device`() {
            val newToken = "fcm-new-token-456"
            every { registerPushTokenUseCase.registerPushToken(userId, deviceId, newToken) } returns Unit

            val response = controller.registerPushToken(
                com.muhabbet.shared.dto.RegisterPushTokenRequest(pushToken = newToken)
            )

            assert(response.statusCode.value() == 200)
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
