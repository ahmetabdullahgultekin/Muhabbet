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

interface ManageBotUseCase {
    fun createBot(command: CreateBotCommand): Bot
    fun getBot(botId: UUID): Bot?
    fun getBotByToken(apiToken: String): Bot?
    fun listBotsByOwner(ownerId: UUID): List<Bot>
    fun updateWebhook(botId: UUID, ownerId: UUID, webhookUrl: String)
    fun deactivateBot(botId: UUID, ownerId: UUID)
    fun regenerateToken(botId: UUID, ownerId: UUID): String
}
