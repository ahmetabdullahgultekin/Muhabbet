package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManageDisappearingMessageUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
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

class DisappearingMessageControllerTest {

    private lateinit var useCase: ManageDisappearingMessageUseCase
    private lateinit var controller: DisappearingMessageController

    private val userId = TestData.USER_ID_1
    private val conversationId = TestData.CONVERSATION_ID

    @BeforeEach
    fun setUp() {
        useCase = mockk()
        controller = DisappearingMessageController(useCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class SetDisappearTimer {

        @Test
        fun `should set disappear timer to 24 hours`() {
            every { useCase.setDisappearTimer(conversationId, userId, 86400) } returns Unit

            val response = controller.setDisappearTimer(
                conversationId,
                SetDisappearTimerRequest(seconds = 86400)
            )

            assert(response.statusCode.value() == 200)
            verify { useCase.setDisappearTimer(conversationId, userId, 86400) }
        }

        @Test
        fun `should disable disappear timer with null`() {
            every { useCase.setDisappearTimer(conversationId, userId, null) } returns Unit

            val response = controller.setDisappearTimer(
                conversationId,
                SetDisappearTimerRequest(seconds = null)
            )

            assert(response.statusCode.value() == 200)
            verify { useCase.setDisappearTimer(conversationId, userId, null) }
        }

        @Test
        fun `should throw CONV_NOT_FOUND for invalid conversation`() {
            every {
                useCase.setDisappearTimer(conversationId, userId, 3600)
            } throws BusinessException(ErrorCode.CONV_NOT_FOUND)

            try {
                controller.setDisappearTimer(conversationId, SetDisappearTimerRequest(seconds = 3600))
                assert(false)
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.CONV_NOT_FOUND)
            }
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
