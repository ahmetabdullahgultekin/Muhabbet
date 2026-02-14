package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.out.StarredMessageRepository
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

class StarredMessageControllerTest {

    private lateinit var starredMessageRepository: StarredMessageRepository
    private lateinit var getMessageHistoryUseCase: GetMessageHistoryUseCase
    private lateinit var controller: StarredMessageController

    private val userId = TestData.USER_ID_1
    private val messageId = TestData.MESSAGE_ID

    @BeforeEach
    fun setUp() {
        starredMessageRepository = mockk()
        getMessageHistoryUseCase = mockk()
        controller = StarredMessageController(starredMessageRepository, getMessageHistoryUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class StarMessage {

        @Test
        fun `should star a message`() {
            every { starredMessageRepository.star(userId, messageId) } returns Unit

            val response = controller.starMessage(messageId)

            assert(response.statusCode.value() == 200)
            verify { starredMessageRepository.star(userId, messageId) }
        }
    }

    @Nested
    inner class UnstarMessage {

        @Test
        fun `should unstar a message`() {
            every { starredMessageRepository.unstar(userId, messageId) } returns Unit

            val response = controller.unstarMessage(messageId)

            assert(response.statusCode.value() == 200)
            verify { starredMessageRepository.unstar(userId, messageId) }
        }
    }

    @Nested
    inner class GetStarredMessages {

        @Test
        fun `should return starred messages with delivery statuses`() {
            val messages = listOf(
                TestData.textMessage(id = UUID.randomUUID(), content = "Starred msg 1"),
                TestData.textMessage(id = UUID.randomUUID(), content = "Starred msg 2")
            )
            val statusMap = messages.associate { it.id to DeliveryStatus.READ }

            every { starredMessageRepository.getStarredMessages(userId, 50, 0) } returns messages
            every { getMessageHistoryUseCase.resolveDeliveryStatuses(messages, userId) } returns statusMap

            val response = controller.getStarredMessages(50, 0)

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.items?.size == 2)
        }

        @Test
        fun `should return empty list when no starred messages`() {
            every { starredMessageRepository.getStarredMessages(userId, 50, 0) } returns emptyList()
            every { getMessageHistoryUseCase.resolveDeliveryStatuses(emptyList(), userId) } returns emptyMap()

            val response = controller.getStarredMessages(50, 0)

            assert(response.body?.data?.items?.isEmpty() == true)
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
