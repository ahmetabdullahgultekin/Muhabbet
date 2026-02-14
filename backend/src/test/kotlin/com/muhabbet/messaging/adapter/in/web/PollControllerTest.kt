package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ManagePollUseCase
import com.muhabbet.messaging.domain.port.`in`.PollOptionInfo
import com.muhabbet.messaging.domain.port.`in`.PollResult
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class PollControllerTest {

    private lateinit var managePollUseCase: ManagePollUseCase
    private lateinit var controller: PollController

    private val userId = TestData.USER_ID_1
    private val messageId = TestData.MESSAGE_ID

    @BeforeEach
    fun setUp() {
        managePollUseCase = mockk()
        controller = PollController(managePollUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class Vote {

        @Test
        fun `should cast vote and return results`() {
            val result = PollResult(
                messageId = messageId,
                options = listOf(
                    PollOptionInfo(index = 0, text = "Option A", count = 3),
                    PollOptionInfo(index = 1, text = "Option B", count = 1)
                ),
                totalVotes = 4,
                myVote = 0
            )

            every { managePollUseCase.vote(messageId, userId, 0) } returns result

            val response = controller.vote(messageId, com.muhabbet.shared.dto.PollVoteRequest(optionIndex = 0))

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.totalVotes == 4)
            assert(response.body?.data?.myVote == 0)
        }

        @Test
        fun `should throw POLL_INVALID_OPTION for out-of-range index`() {
            every {
                managePollUseCase.vote(messageId, userId, 99)
            } throws BusinessException(ErrorCode.POLL_INVALID_OPTION)

            try {
                controller.vote(messageId, com.muhabbet.shared.dto.PollVoteRequest(optionIndex = 99))
                assert(false)
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.POLL_INVALID_OPTION)
            }
        }

        @Test
        fun `should throw POLL_MESSAGE_NOT_FOUND for invalid message`() {
            every {
                managePollUseCase.vote(messageId, userId, 0)
            } throws BusinessException(ErrorCode.POLL_MESSAGE_NOT_FOUND)

            try {
                controller.vote(messageId, com.muhabbet.shared.dto.PollVoteRequest(optionIndex = 0))
                assert(false)
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.POLL_MESSAGE_NOT_FOUND)
            }
        }
    }

    @Nested
    inner class GetResults {

        @Test
        fun `should return poll results with my vote`() {
            val result = PollResult(
                messageId = messageId,
                options = listOf(
                    PollOptionInfo(index = 0, text = "Yes", count = 5),
                    PollOptionInfo(index = 1, text = "No", count = 2)
                ),
                totalVotes = 7,
                myVote = 0
            )

            every { managePollUseCase.getResults(messageId, userId) } returns result

            val response = controller.getResults(messageId)

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.votes?.size == 2)
            assert(response.body?.data?.totalVotes == 7)
        }

        @Test
        fun `should return null myVote when user has not voted`() {
            val result = PollResult(
                messageId = messageId,
                options = listOf(PollOptionInfo(index = 0, text = "A", count = 0)),
                totalVotes = 0,
                myVote = null
            )

            every { managePollUseCase.getResults(messageId, userId) } returns result

            val response = controller.getResults(messageId)

            assert(response.body?.data?.myVote == null)
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
