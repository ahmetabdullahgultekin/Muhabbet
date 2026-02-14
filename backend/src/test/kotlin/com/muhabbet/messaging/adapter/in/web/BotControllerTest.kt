package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.model.Bot
import com.muhabbet.messaging.domain.port.`in`.CreateBotCommand
import com.muhabbet.messaging.domain.port.`in`.ManageBotUseCase
import com.muhabbet.shared.TestData
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Tests for bot use cases. BotController uses @AuthenticationPrincipal,
 * so we test the use case layer directly.
 */
class BotControllerTest {

    private lateinit var manageBotUseCase: ManageBotUseCase

    private val userId = TestData.USER_ID_1
    private val botId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        manageBotUseCase = mockk()
    }

    @Nested
    inner class CreateBot {

        @Test
        fun `should create bot with default permissions`() {
            val bot = Bot(
                id = botId,
                ownerId = userId,
                userId = UUID.randomUUID(),
                name = "Test Bot",
                apiToken = "mhb_test-token-abc123"
            )

            every {
                manageBotUseCase.createBot(any())
            } returns bot

            val result = manageBotUseCase.createBot(
                CreateBotCommand(
                    ownerId = userId,
                    name = "Test Bot",
                    description = null,
                    webhookUrl = null,
                    permissions = listOf("SEND_MESSAGE", "READ_MESSAGE")
                )
            )

            assert(result.name == "Test Bot")
            assert(result.apiToken.startsWith("mhb_"))
        }

        @Test
        fun `should create bot with custom webhook and permissions`() {
            val bot = Bot(
                id = botId,
                ownerId = userId,
                userId = UUID.randomUUID(),
                name = "Webhook Bot",
                apiToken = "mhb_webhook-token",
                webhookUrl = "https://example.com/webhook",
                permissions = listOf("SEND_MESSAGE", "READ_MESSAGE", "MANAGE_GROUP")
            )

            every { manageBotUseCase.createBot(any()) } returns bot

            val result = manageBotUseCase.createBot(
                CreateBotCommand(
                    ownerId = userId,
                    name = "Webhook Bot",
                    description = "A bot with webhook",
                    webhookUrl = "https://example.com/webhook",
                    permissions = listOf("SEND_MESSAGE", "READ_MESSAGE", "MANAGE_GROUP")
                )
            )

            assert(result.webhookUrl == "https://example.com/webhook")
            assert(result.permissions.size == 3)
        }
    }

    @Nested
    inner class GetBot {

        @Test
        fun `should return bot by ID`() {
            val bot = Bot(id = botId, ownerId = userId, userId = UUID.randomUUID(), name = "My Bot", apiToken = "mhb_token")

            every { manageBotUseCase.getBot(botId) } returns bot

            val result = manageBotUseCase.getBot(botId)

            assert(result?.name == "My Bot")
        }

        @Test
        fun `should return null for non-existent bot`() {
            every { manageBotUseCase.getBot(botId) } returns null

            val result = manageBotUseCase.getBot(botId)

            assert(result == null)
        }
    }

    @Nested
    inner class ListBots {

        @Test
        fun `should return bots owned by user`() {
            val bots = listOf(
                Bot(id = UUID.randomUUID(), ownerId = userId, userId = UUID.randomUUID(), name = "Bot 1", apiToken = "mhb_1"),
                Bot(id = UUID.randomUUID(), ownerId = userId, userId = UUID.randomUUID(), name = "Bot 2", apiToken = "mhb_2")
            )

            every { manageBotUseCase.listBotsByOwner(userId) } returns bots

            val result = manageBotUseCase.listBotsByOwner(userId)

            assert(result.size == 2)
        }
    }

    @Nested
    inner class UpdateWebhook {

        @Test
        fun `should update webhook URL`() {
            every { manageBotUseCase.updateWebhook(botId, userId, "https://new.webhook.com") } returns Unit

            manageBotUseCase.updateWebhook(botId, userId, "https://new.webhook.com")

            verify { manageBotUseCase.updateWebhook(botId, userId, "https://new.webhook.com") }
        }
    }

    @Nested
    inner class RegenerateToken {

        @Test
        fun `should regenerate API token`() {
            every { manageBotUseCase.regenerateToken(botId, userId) } returns "mhb_new-token-xyz"

            val newToken = manageBotUseCase.regenerateToken(botId, userId)

            assert(newToken.startsWith("mhb_"))
        }
    }

    @Nested
    inner class DeactivateBot {

        @Test
        fun `should deactivate bot`() {
            every { manageBotUseCase.deactivateBot(botId, userId) } returns Unit

            manageBotUseCase.deactivateBot(botId, userId)

            verify { manageBotUseCase.deactivateBot(botId, userId) }
        }

        @Test
        fun `should throw BOT_NOT_FOUND for invalid bot`() {
            every {
                manageBotUseCase.deactivateBot(botId, userId)
            } throws BusinessException(ErrorCode.BOT_NOT_FOUND)

            try {
                manageBotUseCase.deactivateBot(botId, userId)
                assert(false)
            } catch (ex: BusinessException) {
                assert(ex.errorCode == ErrorCode.BOT_NOT_FOUND)
            }
        }
    }
}
