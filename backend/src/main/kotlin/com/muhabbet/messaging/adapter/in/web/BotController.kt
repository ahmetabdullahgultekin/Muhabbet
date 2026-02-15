package com.muhabbet.messaging.adapter.`in`.web

import com.muhabbet.messaging.domain.port.`in`.CreateBotCommand
import com.muhabbet.messaging.domain.port.`in`.ManageBotUseCase
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
        val bot = manageBotUseCase.createBot(
            CreateBotCommand(
                ownerId = userId,
                name = request.name,
                description = request.description,
                webhookUrl = request.webhookUrl,
                permissions = request.permissions ?: listOf("SEND_MESSAGE", "READ_MESSAGE")
            )
        )
        return ApiResponseBuilder.ok(
            mapOf(
                "botId" to bot.id.toString(),
                "apiToken" to bot.apiToken,
                "name" to bot.name,
                "userId" to bot.userId.toString()
            )
        )
    }

    @GetMapping
    fun listMyBots(): ResponseEntity<*> {
        val userId = AuthenticatedUser.currentUserId()
        val bots = manageBotUseCase.listBotsByOwner(userId)
        return ApiResponseBuilder.ok(bots.map { bot ->
            mapOf(
                "botId" to bot.id.toString(),
                "name" to bot.name,
                "description" to bot.description,
                "isActive" to bot.isActive,
                "webhookUrl" to bot.webhookUrl,
                "createdAt" to bot.createdAt.toString()
            )
        })
    }

    @GetMapping("/{botId}")
    fun getBot(
        @PathVariable botId: String
    ): ResponseEntity<*> {
        val bot = manageBotUseCase.getBot(UUID.fromString(botId))
        return ApiResponseBuilder.ok(bot)
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
