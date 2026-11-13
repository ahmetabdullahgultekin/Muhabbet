package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.StarredCursor
import com.muhabbet.messaging.domain.model.StarredMessage
import com.muhabbet.messaging.domain.model.StarredMessagePage
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.GetStarredMessagesUseCase
import com.muhabbet.messaging.domain.port.`in`.StarMessageUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

class StarredMessageControllerTest {

    private lateinit var starMessageUseCase: StarMessageUseCase
    private lateinit var getStarredMessagesUseCase: GetStarredMessagesUseCase
    private lateinit var getMessageHistoryUseCase: GetMessageHistoryUseCase
    private lateinit var controller: StarredMessageController

    private val userId = TestData.USER_ID_1
    private val messageId = TestData.MESSAGE_ID

    @BeforeEach
    fun setUp() {
        starMessageUseCase = mockk()
        getStarredMessagesUseCase = mockk()
        getMessageHistoryUseCase = mockk()
        controller = StarredMessageController(starMessageUseCase, getStarredMessagesUseCase, getMessageHistoryUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class StarMessage {

        @Test
        fun `should delegate star to the use case with the authenticated user`() {
            justRun { starMessageUseCase.star(userId, messageId) }

            val response = controller.starMessage(messageId)

            assertEquals(200, response.statusCode.value())
            verify { starMessageUseCase.star(userId, messageId) }
        }
    }

    @Nested
    inner class UnstarMessage {

        @Test
        fun `should delegate unstar to the use case with the authenticated user`() {
            justRun { starMessageUseCase.unstar(userId, messageId) }

            val response = controller.unstarMessage(messageId)

            assertEquals(200, response.statusCode.value())
            verify { starMessageUseCase.unstar(userId, messageId) }
        }
    }

    @Nested
    inner class GetStarredMessages {

        @Test
        fun `should return the page with delivery statuses and an encoded cursor`() {
            val messages = listOf(
                TestData.textMessage(id = UUID.randomUUID(), content = "Starred msg 1"),
                TestData.textMessage(id = UUID.randomUUID(), content = "Starred msg 2")
            )
            val stars = messages.map { StarredMessage(UUID.randomUUID(), Instant.now(), it) }
            val cursor = StarredCursor(stars.last().starredAt, stars.last().starId)
            every { getStarredMessagesUseCase.getStarred(userId, null, 2) } returns
                StarredMessagePage(items = stars, nextCursor = cursor, hasMore = true)
            every { getMessageHistoryUseCase.resolveDeliveryStatuses(messages, userId) } returns
                messages.associate { it.id to DeliveryStatus.READ }

            val body = controller.getStarredMessages(2, null).body?.data

            assertEquals(messages.map { it.id.toString() }, body?.items?.map { it.id })
            assertEquals(cursor.encode(), body?.nextCursor)
            assertTrue(body?.hasMore == true)
        }

        @Test
        fun `should return an empty last page with no cursor`() {
            every { getStarredMessagesUseCase.getStarred(userId, "c", 50) } returns
                StarredMessagePage(items = emptyList(), nextCursor = null, hasMore = false)
            every { getMessageHistoryUseCase.resolveDeliveryStatuses(emptyList(), userId) } returns emptyMap()

            val body = controller.getStarredMessages(50, "c").body?.data

            assertTrue(body?.items?.isEmpty() == true)
            assertNull(body?.nextCursor)
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
