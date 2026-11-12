package com.muhabbet.messaging.domain.service

import com.muhabbet.messaging.domain.model.Bot
import com.muhabbet.messaging.domain.model.BotPermission
import com.muhabbet.messaging.domain.port.`in`.CreateBotCommand
import com.muhabbet.messaging.domain.port.`in`.IssuedBot
import com.muhabbet.messaging.domain.port.`in`.ManageBotUseCase
import com.muhabbet.messaging.domain.port.out.BotRepository
import com.muhabbet.auth.domain.port.out.UserRepository
import com.muhabbet.shared.exception.BusinessException
import com.muhabbet.shared.exception.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

open class BotService(
    private val botRepository: BotRepository,
    private val userRepository: UserRepository
) : ManageBotUseCase {

    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        private const val TOKEN_BYTES = 32

        /**
         * SHA-256 of the token, hex. A plain digest rather than a slow password hash is right here:
         * the token is 256 random bits, so there is nothing to brute-force, and lookups need a
         * deterministic value to index on. Matches V33's conversion of the tokens already stored.
         */
        fun hashToken(token: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(token.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }

    @Transactional
    override fun createBot(command: CreateBotCommand): IssuedBot {
        val unknown = command.permissions.filter { name -> BotPermission.entries.none { it.name == name } }
        if (unknown.isNotEmpty()) {
            throw BusinessException(ErrorCode.VALIDATION_ERROR)
        }
        // Create a user account for the bot
        val botUserId = UUID.randomUUID()
        val apiToken = generateApiToken()

        val bot = botRepository.save(
            Bot(
                ownerId = command.ownerId,
                userId = botUserId,
                name = command.name,
                description = command.description,
                apiTokenHash = hashToken(apiToken),
                webhookUrl = command.webhookUrl,
                permissions = command.permissions
            )
        )
        log.info("Bot created: id={}, name={}, owner={}", bot.id, bot.name, command.ownerId)
        return IssuedBot(bot, apiToken)
    }

    @Transactional(readOnly = true)
    override fun getBot(botId: UUID, requesterId: UUID): Bot {
        val bot = botRepository.findById(botId)
        if (bot == null || bot.ownerId != requesterId) {
            throw BusinessException(ErrorCode.BOT_NOT_FOUND)
        }
        return bot
    }

    @Transactional(readOnly = true)
    override fun getBotByToken(apiToken: String): Bot? {
        return botRepository.findByApiToken(hashToken(apiToken))
    }

    @Transactional(readOnly = true)
    override fun listBotsByOwner(ownerId: UUID): List<Bot> {
        return botRepository.findByOwnerId(ownerId)
    }

    @Transactional
    override fun updateWebhook(botId: UUID, ownerId: UUID, webhookUrl: String) {
        val bot = botRepository.findById(botId)
            ?: throw BusinessException(ErrorCode.BOT_NOT_FOUND)
        if (bot.ownerId != ownerId) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
        botRepository.update(bot.copy(webhookUrl = webhookUrl, updatedAt = Instant.now()))
    }

    @Transactional
    override fun deactivateBot(botId: UUID, ownerId: UUID) {
        val bot = botRepository.findById(botId)
            ?: throw BusinessException(ErrorCode.BOT_NOT_FOUND)
        if (bot.ownerId != ownerId) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
        botRepository.update(bot.copy(isActive = false, updatedAt = Instant.now()))
        log.info("Bot deactivated: id={}", botId)
    }

    @Transactional
    override fun regenerateToken(botId: UUID, ownerId: UUID): String {
        val bot = botRepository.findById(botId)
            ?: throw BusinessException(ErrorCode.BOT_NOT_FOUND)
        if (bot.ownerId != ownerId) {
            throw BusinessException(ErrorCode.GROUP_PERMISSION_DENIED)
        }
        val newToken = generateApiToken()
        botRepository.update(bot.copy(apiTokenHash = hashToken(newToken), updatedAt = Instant.now()))
        log.info("Bot token regenerated: id={}", botId)
        return newToken
    }

    private fun generateApiToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return "mhb_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
