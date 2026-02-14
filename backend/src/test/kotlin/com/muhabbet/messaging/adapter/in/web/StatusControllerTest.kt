package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.Status
import com.muhabbet.messaging.domain.port.`in`.ManageStatusUseCase
import com.muhabbet.messaging.domain.port.`in`.StatusGroup
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
import java.time.Instant
import java.util.UUID

class StatusControllerTest {

    private lateinit var manageStatusUseCase: ManageStatusUseCase
    private lateinit var controller: StatusController

    private val userId = TestData.USER_ID_1

    @BeforeEach
    fun setUp() {
        manageStatusUseCase = mockk()
        controller = StatusController(manageStatusUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class CreateStatus {

        @Test
        fun `should create text status`() {
            val status = Status(userId = userId, content = "Hello world!")

            every {
                manageStatusUseCase.createStatus(userId, "Hello world!", null)
            } returns status

            val response = controller.createStatus(
                com.muhabbet.shared.dto.StatusCreateRequest(content = "Hello world!", mediaUrl = null)
            )

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.content == "Hello world!")
        }

        @Test
        fun `should create media status`() {
            val status = Status(userId = userId, mediaUrl = "https://media.muhabbet.com/status/img.jpg")

            every {
                manageStatusUseCase.createStatus(userId, null, "https://media.muhabbet.com/status/img.jpg")
            } returns status

            val response = controller.createStatus(
                com.muhabbet.shared.dto.StatusCreateRequest(content = null, mediaUrl = "https://media.muhabbet.com/status/img.jpg")
            )

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.mediaUrl == "https://media.muhabbet.com/status/img.jpg")
        }
    }

    @Nested
    inner class GetMyStatuses {

        @Test
        fun `should return user statuses`() {
            val statuses = listOf(
                Status(userId = userId, content = "Status 1"),
                Status(userId = userId, content = "Status 2")
            )

            every { manageStatusUseCase.getMyStatuses(userId) } returns statuses

            val response = controller.getMyStatuses()

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.size == 2)
        }

        @Test
        fun `should return empty list when no statuses`() {
            every { manageStatusUseCase.getMyStatuses(userId) } returns emptyList()

            val response = controller.getMyStatuses()

            assert(response.body?.data?.isEmpty() == true)
        }
    }

    @Nested
    inner class GetContactStatuses {

        @Test
        fun `should return grouped contact statuses`() {
            val groups = listOf(
                StatusGroup(
                    userId = TestData.USER_ID_2,
                    statuses = listOf(Status(userId = TestData.USER_ID_2, content = "Hey"))
                )
            )

            every { manageStatusUseCase.getContactStatuses() } returns groups

            val response = controller.getContactStatuses()

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.size == 1)
        }
    }

    @Nested
    inner class DeleteStatus {

        @Test
        fun `should delete own status`() {
            val statusId = UUID.randomUUID()
            every { manageStatusUseCase.deleteStatus(statusId, userId) } returns Unit

            val response = controller.deleteStatus(statusId)

            assert(response.statusCode.value() == 200)
            verify { manageStatusUseCase.deleteStatus(statusId, userId) }
        }

        @Test
        fun `should throw STATUS_NOT_FOUND for invalid status`() {
            val statusId = UUID.randomUUID()
            every {
                manageStatusUseCase.deleteStatus(statusId, userId)
            } throws BusinessException(ErrorCode.STATUS_NOT_FOUND)

            try {
                controller.deleteStatus(statusId)
                assert(false) { "Expected BusinessException" }
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.STATUS_NOT_FOUND)
            }
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
