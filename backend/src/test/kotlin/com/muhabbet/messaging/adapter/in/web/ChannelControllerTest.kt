package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.ChannelInfo
import com.muhabbet.messaging.domain.port.`in`.ManageChannelUseCase
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

class ChannelControllerTest {

    private lateinit var manageChannelUseCase: ManageChannelUseCase
    private lateinit var controller: ChannelController

    private val userId = TestData.USER_ID_1
    private val channelId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        manageChannelUseCase = mockk()
        controller = ChannelController(manageChannelUseCase)
        setAuthenticatedUser(userId, TestData.DEVICE_ID_1)
    }

    @Nested
    inner class Subscribe {

        @Test
        fun `should subscribe to channel`() {
            every { manageChannelUseCase.subscribe(channelId, userId) } returns Unit

            val response = controller.subscribe(channelId)

            assert(response.statusCode.value() == 200)
            verify { manageChannelUseCase.subscribe(channelId, userId) }
        }

        @Test
        fun `should throw CHANNEL_NOT_FOUND for invalid channel`() {
            every {
                manageChannelUseCase.subscribe(channelId, userId)
            } throws BusinessException(ErrorCode.CHANNEL_NOT_FOUND)

            try {
                controller.subscribe(channelId)
                assert(false)
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.CHANNEL_NOT_FOUND)
            }
        }
    }

    @Nested
    inner class Unsubscribe {

        @Test
        fun `should unsubscribe from channel`() {
            every { manageChannelUseCase.unsubscribe(channelId, userId) } returns Unit

            val response = controller.unsubscribe(channelId)

            assert(response.statusCode.value() == 200)
            verify { manageChannelUseCase.unsubscribe(channelId, userId) }
        }
    }

    @Nested
    inner class ListChannels {

        @Test
        fun `should return list of channels with subscription status`() {
            val channels = listOf(
                ChannelInfo(id = channelId, name = "News", description = "Daily news", subscriberCount = 150, isSubscribed = true, createdAt = "2026-01-01"),
                ChannelInfo(id = UUID.randomUUID(), name = "Tech", description = null, subscriberCount = 50, isSubscribed = false, createdAt = "2026-02-01")
            )

            every { manageChannelUseCase.listChannels(userId) } returns channels

            val response = controller.listChannels()

            assert(response.statusCode.value() == 200)
            assert(response.body?.data?.size == 2)
            assert(response.body?.data?.first()?.isSubscribed == true)
            assert(response.body?.data?.last()?.isSubscribed == false)
        }

        @Test
        fun `should return empty list when no channels exist`() {
            every { manageChannelUseCase.listChannels(userId) } returns emptyList()

            val response = controller.listChannels()

            assert(response.body?.data?.isEmpty() == true)
        }
    }

    private fun setAuthenticatedUser(userId: UUID, deviceId: UUID) {
        val claims = JwtClaims(userId = userId, deviceId = deviceId)
        val auth = UsernamePasswordAuthenticationToken(claims, null, emptyList())
        SecurityContextHolder.getContext().authentication = auth
    }
}
