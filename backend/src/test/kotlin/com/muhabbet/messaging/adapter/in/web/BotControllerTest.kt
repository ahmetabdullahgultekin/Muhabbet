package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.Bot
import com.muhabbet.messaging.domain.port.`in`.IssuedBot
import com.muhabbet.messaging.domain.port.`in`.ManageBotUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import com.muhabbet.shared.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class BotControllerTest {

    private lateinit var useCase: ManageBotUseCase
    private lateinit var controller: BotController

    private val userId = TestData.USER_ID_1
    private val bot = Bot(ownerId = userId, userId = UUID.randomUUID(), name = "Bot", apiTokenHash = "stored-hash")

    @BeforeEach
    fun setUp() {
        useCase = mockk()
        controller = BotController(useCase)
        val claims = JwtClaims(userId = userId, deviceId = TestData.DEVICE_ID_1)
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(claims, null, emptyList())
    }

    @Test
    fun `should return the token once when a bot is created`() {
        every { useCase.createBot(any()) } returns IssuedBot(bot, "mhb_shown-once")

        val body = controller.createBot(CreateBotRequest(name = "Bot")).body.toString()

        assert(body.contains("mhb_shown-once"))
    }

    @Test
    fun `should never include the token or its hash when a bot is read`() {
        every { useCase.getBot(bot.id, userId) } returns bot

        val body = controller.getBot(bot.id.toString()).body.toString()

        assertFalse(body.contains("stored-hash"))
        assertFalse(body.contains("apiToken"))
        verify { useCase.getBot(bot.id, userId) }
    }

    @Test
    fun `should pass the caller through when a bot is read`() {
        every { useCase.getBot(bot.id, userId) } throws BusinessException(ErrorCode.BOT_NOT_FOUND)

        val ex = assertThrows<BusinessException> { controller.getBot(bot.id.toString()) }

        assertEquals(ErrorCode.BOT_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should never include the token hash when bots are listed`() {
        every { useCase.listBotsByOwner(userId) } returns listOf(bot)

        assertFalse(controller.listMyBots().body.toString().contains("stored-hash"))
    }
}
