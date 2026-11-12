package com.muhabbet.messaging.domain.port.`in`

import com.muhabbet.messaging.domain.model.Bot
import java.util.UUID

data class CreateBotCommand(
    val ownerId: UUID,
    val name: String,
    val description: String?,
    val webhookUrl: String?,
    val permissions: List<String> = listOf("SEND_MESSAGE", "READ_MESSAGE")
)

/** A bot as just issued: the only moment its plaintext [apiToken] exists outside the caller. */
data class IssuedBot(val bot: Bot, val apiToken: String)

interface ManageBotUseCase {
    fun createBot(command: CreateBotCommand): IssuedBot
    /** The bot, for its owner only; anyone else gets [com.muhabbet.shared.exception.ErrorCode.BOT_NOT_FOUND]. */
    fun getBot(botId: UUID, requesterId: UUID): Bot
    fun getBotByToken(apiToken: String): Bot?
    fun listBotsByOwner(ownerId: UUID): List<Bot>
    fun updateWebhook(botId: UUID, ownerId: UUID, webhookUrl: String)
    fun deactivateBot(botId: UUID, ownerId: UUID)
    fun regenerateToken(botId: UUID, ownerId: UUID): String
}
