package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.CreateBotCommand
import com.muhabbet.messaging.domain.port.`in`.ManageBotUseCase
import com.muhabbet.messaging.domain.model.Bot
import com.muhabbet.shared.security.AuthenticatedUser
import com.muhabbet.shared.web.ApiResponseBuilder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/bots")
class BotController(
    private val manageBotUseCase: ManageBotUseCase
) {

    @PostMapping
    fun createBot(
        @RequestBody request: CreateBotRequest
    ): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        val issued = manageBotUseCase.createBot(
            CreateBotCommand(
                ownerId = userId,
                name = request.name,
                description = request.description,
                webhookUrl = request.webhookUrl,
                permissions = request.permissions ?: listOf("SEND_MESSAGE", "READ_MESSAGE")
            )
        )
        // The only response that ever carries the token: it is stored hashed and cannot be shown again.
        return ApiResponseBuilder.ok(
            mapOf(
                "botId" to issued.bot.id.toString(),
                "apiToken" to issued.apiToken,
                "name" to issued.bot.name,
                "userId" to issued.bot.userId.toString()
            )
        )
    }

    @GetMapping
    fun listMyBots(): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        val bots = manageBotUseCase.listBotsByOwner(userId)
        return ApiResponseBuilder.ok(bots.map { it.toSummary() })
    }

    @GetMapping("/{botId}")
    fun getBot(
        @PathVariable botId: String
    ): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        val bot = manageBotUseCase.getBot(UUID.fromString(botId), userId)
        return ApiResponseBuilder.ok(bot.toSummary())
    }

    @PatchMapping("/{botId}/webhook")
    fun updateWebhook(
        @PathVariable botId: String,
        @RequestBody request: UpdateWebhookRequest
    ): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        manageBotUseCase.updateWebhook(UUID.fromString(botId), userId, request.webhookUrl)
        return ApiResponseBuilder.ok(mapOf("updated" to true))
    }

    @PostMapping("/{botId}/regenerate-token")
    fun regenerateToken(
        @PathVariable botId: String
    ): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        val newToken = manageBotUseCase.regenerateToken(UUID.fromString(botId), userId)
        return ApiResponseBuilder.ok(mapOf("apiToken" to newToken))
    }

    @DeleteMapping("/{botId}")
    fun deactivateBot(
        @PathVariable botId: String
    ): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        manageBotUseCase.deactivateBot(UUID.fromString(botId), userId)
        return ApiResponseBuilder.ok(mapOf("deactivated" to true))
    }
}

data class CreateBotRequest(
    val name: String,
    val description: String? = null,
    val webhookUrl: String? = null,
    val permissions: List<String>? = null
)

data class UpdateWebhookRequest(
    val webhookUrl: String
)

/** What a bot's owner sees. Never the token or its hash. */
private fun Bot.toSummary(): Map<String, Any?> = mapOf(
    "botId" to id.toString(),
    "name" to name,
    "description" to description,
    "isActive" to isActive,
    "webhookUrl" to webhookUrl,
    "permissions" to permissions,
    "createdAt" to createdAt.toString()
)
