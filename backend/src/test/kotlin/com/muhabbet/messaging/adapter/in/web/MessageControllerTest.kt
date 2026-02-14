package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.model.DeliveryStatus
import com.muhabbet.messaging.domain.model.Message
import com.muhabbet.messaging.domain.port.`in`.GetMessageHistoryUseCase
import com.muhabbet.messaging.domain.port.`in`.ManageMessageUseCase
import com.muhabbet.messaging.domain.port.`in`.MessagePage
import com.muhabbet.messaging.domain.port.out.MessageRepository
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

class MessageControllerTest {

    private lateinit var getMessageHistoryUseCase: GetMessageHistoryUseCase
    private lateinit var manageMessageUseCase: ManageMessageUseCase
    private lateinit var messageRepository: MessageRepository
    private lateinit var userRepository: UserRepository
    private lateinit var controller: MessageController

    private val userId = TestData.USER_ID_1
    private val deviceId = TestData.DEVICE_ID_1
    private val conversationId = TestData.CONVERSATION_ID
    private val messageId = TestData.MESSAGE_ID

    @BeforeEach
    fun setUp() {
        getMessageHistoryUseCase = mockk()
        manageMessageUseCase = mockk()
        messageRepository = mockk()
        userRepository = mockk()
        controller = MessageController(
            getMessageHistoryUseCase = getMessageHistoryUseCase,
            manageMessageUseCase = manageMessageUseCase,
            messageRepository = messageRepository,
            userRepository = userRepository
        )
        setAuthenticatedUser(userId, deviceId)
    }

    @Nested
    inner class GetMessages {

        @Test
        fun `should return paginated messages with delivery statuses`() {
            val messages = listOf(
                TestData.textMessage(id = UUID.randomUUID(), content = "msg1"),
                TestData.textMessage(id = UUID.randomUUID(), content = "msg2")
            )
            val page = MessagePage(items = messages, nextCursor = null, hasMore = false)
            val statusMap = messages.associate { it.id to DeliveryStatus.DELIVERED }

            every {
                getMessageHistoryUseCase.getMessages(conversationId, userId, null, 50, "before")
            } returns page
            every {
                getMessageHistoryUseCase.resolveDeliveryStatuses(messages, userId)
            } returns statusMap

            val response = controller.getMessages(conversationId, null, 50, "before")

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.items?.size == 2)
            assert(response.body?.data?.hasMore == false)
        }

        @Test
        fun `should return empty list when no messages exist`() {
            val page = MessagePage(items = emptyList(), nextCursor = null, hasMore = false)

            every {
                getMessageHistoryUseCase.getMessages(conversationId, userId, null, 50, "before")
            } returns page
            every {
                getMessageHistoryUseCase.resolveDeliveryStatuses(emptyList(), userId)
            } returns emptyMap()

            val response = controller.getMessages(conversationId, null, 50, "before")

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.items.isNullOrEmpty())
        }

        @Test
        fun `should support cursor-based pagination`() {
            val messages = listOf(TestData.textMessage())
            val page = MessagePage(items = messages, nextCursor = "cursor-abc", hasMore = true)
            val statusMap = messages.associate { it.id to DeliveryStatus.SENT }

            every {
                getMessageHistoryUseCase.getMessages(conversationId, userId, "prev-cursor", 20, "before")
            } returns page
            every {
                getMessageHistoryUseCase.resolveDeliveryStatuses(messages, userId)
            } returns statusMap

            val response = controller.getMessages(conversationId, "prev-cursor", 20, "before")

            assert(response.body?.data?.nextCursor == "cursor-abc")
            assert(response.body?.data?.hasMore == true)
        }
    }

    @Nested
    inner class GetMessageInfo {

        @Test
        fun `should return message info with delivery statuses`() {
            val message = TestData.textMessage()
            val deliveryStatuses = listOf(
                TestData.deliveryStatus(messageId = message.id, userId = TestData.USER_ID_2, status = DeliveryStatus.READ)
            )

            every { messageRepository.findById(message.id) } returns message
            every { messageRepository.getDeliveryStatuses(listOf(message.id)) } returns deliveryStatuses
            every { userRepository.findById(TestData.USER_ID_2) } returns TestData.user(id = TestData.USER_ID_2, displayName = "User 2")

            val response = controller.getMessageInfo(message.id)

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.messageId == message.id.toString())
            assert(response.body?.data?.recipients?.size == 1)
            assert(response.body?.data?.recipients?.first()?.status == "READ")
        }

        @Test
        fun `should throw MSG_NOT_FOUND when message does not exist`() {
            every { messageRepository.findById(messageId) } returns null

            try {
                controller.getMessageInfo(messageId)
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.MSG_NOT_FOUND)
            }
        }

        @Test
        fun `should return empty content for deleted messages`() {
            val deleted = TestData.deletedMessage(id = messageId)

            every { messageRepository.findById(messageId) } returns deleted
            every { messageRepository.getDeliveryStatuses(listOf(messageId)) } returns emptyList()

            val response = controller.getMessageInfo(messageId)

            assert(response.body?.data?.content == "")
        }
    }

    @Nested
    inner class DeleteMessage {

        @Test
        fun `should delete message for sender`() {
            every { manageMessageUseCase.deleteMessage(messageId, userId) } returns Unit

            val response = controller.deleteMessage(messageId)

            assert(response.statusCode.value() == 200)
            verify { manageMessageUseCase.deleteMessage(messageId, userId) }
        }

        @Test
        fun `should propagate error when non-sender tries to delete`() {
            every {
                manageMessageUseCase.deleteMessage(messageId, userId)
            } throws BusinessException(ErrorCode.MSG_NOT_SENDER)

            try {
                controller.deleteMessage(messageId)
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.MSG_NOT_SENDER)
            }
        }
    }

    @Nested
    inner class EditMessage {

        @Test
        fun `should edit message and return updated shared message`() {
            val updatedMsg = TestData.textMessage(content = "Updated content")

            every {
                manageMessageUseCase.editMessage(messageId, userId, "Updated content")
            } returns updatedMsg

            val response = controller.editMessage(
                messageId,
                com.muhabbet.shared.dto.EditMessageRequest("Updated content")
            )

            assert(response.statusCode.value() == 200)
            verify { manageMessageUseCase.editMessage(messageId, userId, "Updated content") }
        }

        @Test
        fun `should propagate error when edit window expired`() {
            every {
                manageMessageUseCase.editMessage(messageId, userId, "late edit")
            } throws BusinessException(ErrorCode.MSG_EDIT_WINDOW_EXPIRED)

            try {
                controller.editMessage(messageId, com.muhabbet.shared.dto.EditMessageRequest("late edit"))
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.MSG_EDIT_WINDOW_EXPIRED)
            }
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
