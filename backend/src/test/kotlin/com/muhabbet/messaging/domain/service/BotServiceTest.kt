package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Bot
import com.muhabbet.messaging.domain.port.`in`.CreateBotCommand
import com.muhabbet.messaging.domain.port.out.BotRepository
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

/**
 * A bot's API token is a password: it is shown once, when issued, and only a one-way hash of it is
 * kept. A bot's details are its owner's to read.
 */
class BotServiceTest {

    private lateinit var bots: BotRepository
    private lateinit var service: BotService

    private val owner = TestData.USER_ID_1
    private val stranger = TestData.USER_ID_2

    @BeforeEach
    fun setUp() {
        bots = mockk(relaxed = true)
        service = BotService(bots, mockk(relaxed = true))
    }

    private fun command() = CreateBotCommand(ownerId = owner, name = "Bot", description = null, webhookUrl = null)

    @Test
    fun `should store only a hash of the token when a bot is created`() {
        val saved = slot<Bot>()
        every { bots.save(capture(saved)) } answers { firstArg() }

        val issued = service.createBot(command())

        assertTrue(issued.apiToken.startsWith("mhb_"))
        assertNotEquals(issued.apiToken, saved.captured.apiTokenHash)
        assertEquals(BotService.hashToken(issued.apiToken), saved.captured.apiTokenHash)
    }

    @Test
    fun `should find a bot by its token when only the hash is stored`() {
        val bot = Bot(ownerId = owner, userId = UUID.randomUUID(), name = "Bot", apiTokenHash = BotService.hashToken("mhb_x"))
        every { bots.findByApiToken(BotService.hashToken("mhb_x")) } returns bot

        assertEquals(bot, service.getBotByToken("mhb_x"))
    }

    @Test
    fun `should refuse to show a bot when the caller is not its owner`() {
        val bot = Bot(ownerId = owner, userId = UUID.randomUUID(), name = "Bot", apiTokenHash = "h")
        every { bots.findById(bot.id) } returns bot

        val ex = assertThrows<BusinessException> { service.getBot(bot.id, stranger) }

        assertEquals(ErrorCode.BOT_NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `should refuse a permission the platform does not define`() {
        val ex = assertThrows<BusinessException> {
            service.createBot(command().copy(permissions = listOf("SEND_MESSAGE", "ROOT")))
        }

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.errorCode)
    }

    @Test
    fun `should store a new hash when the token is regenerated`() {
        val bot = Bot(ownerId = owner, userId = UUID.randomUUID(), name = "Bot", apiTokenHash = "old")
        every { bots.findById(bot.id) } returns bot
        val updated = slot<Bot>()
        every { bots.update(capture(updated)) } answers { firstArg() }

        val token = service.regenerateToken(bot.id, owner)

        assertEquals(BotService.hashToken(token), updated.captured.apiTokenHash)
    }
}
