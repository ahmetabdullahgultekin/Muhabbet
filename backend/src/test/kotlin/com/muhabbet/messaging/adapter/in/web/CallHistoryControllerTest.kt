package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.messaging.domain.port.`in`.CallHistoryPage
import com.muhabbet.messaging.domain.port.`in`.GetCallHistoryUseCase
import com.muhabbet.messaging.domain.service.CallHistoryRecord
import com.muhabbet.shared.TestData
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Instant
import java.util.UUID

class CallHistoryControllerTest {

    private lateinit var getCallHistoryUseCase: GetCallHistoryUseCase
    private lateinit var userRepository: UserRepository
    private lateinit var controller: CallHistoryController

    private val userId = TestData.USER_ID_1

    @BeforeEach
    fun setUp() {
        getCallHistoryUseCase = mockk()
        userRepository = mockk()
        controller = CallHistoryController(getCallHistoryUseCase, userRepository)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class GetCallHistory {

        @Test
        fun `should return paginated call history with user names`() {
            val records = listOf(
                CallHistoryRecord(
                    id = UUID.randomUUID(),
                    callId = "call-123",
                    callerId = userId,
                    calleeId = TestData.USER_ID_2,
                    callType = "VOICE",
                    status = "COMPLETED",
                    startedAt = Instant.now(),
                    answeredAt = Instant.now(),
                    endedAt = Instant.now(),
                    durationSeconds = 120
                )
            )
            val page = CallHistoryPage(items = records, totalCount = 1, page = 0, size = 20, hasMore = false)

            every { getCallHistoryUseCase.getCallHistory(userId, 0, 20) } returns page
            every { userRepository.findAllByIds(any()) } returns listOf(
                TestData.user(id = userId, displayName = "Caller"),
                TestData.user(id = TestData.USER_ID_2, displayName = "Callee")
            )

            val response = controller.getCallHistory(0, 20)

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.items?.size == 1)
            assert(response.body?.data?.items?.first()?.callerName == "Caller")
            assert(response.body?.data?.items?.first()?.calleeName == "Callee")
            assert(response.body?.data?.hasMore == false)
        }

        @Test
        fun `should return empty history for new user`() {
            val page = CallHistoryPage(items = emptyList(), totalCount = 0, page = 0, size = 20, hasMore = false)

            every { getCallHistoryUseCase.getCallHistory(userId, 0, 20) } returns page
            every { userRepository.findAllByIds(any()) } returns emptyList()

            val response = controller.getCallHistory(0, 20)

            assert(response.body?.data?.items?.isEmpty() == true)
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
