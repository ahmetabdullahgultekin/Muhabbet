package com.muhabbet.messaging.adapter.out.persistence

import com.muhabbet.messaging.adapter.out.persistence.entity.BotJpaEntity
import com.muhabbet.messaging.domain.model.Bot
import com.muhabbet.messaging.domain.port.out.BotRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Component
import java.util.UUID

interface SpringDataBotRepository : JpaRepository<BotJpaEntity, UUID> {
    fun findByApiToken(apiToken: String): BotJpaEntity?
    fun findByOwnerId(ownerId: UUID): List<BotJpaEntity>
}

@Component
class BotPersistenceAdapter(
    private val springDataBotRepository: SpringDataBotRepository
) : BotRepository {

    override fun save(bot: Bot): Bot {
        val entity = BotJpaEntity(
            id = bot.id,
            ownerId = bot.ownerId,
            userId = bot.userId,
            name = bot.name,
            description = bot.description,
            apiToken = bot.apiToken,
            webhookUrl = bot.webhookUrl,
            isActive = bot.isActive,
            permissions = bot.permissions.joinToString(",") { "\"$it\"" }.let { "[$it]" },
            createdAt = bot.createdAt,
            updatedAt = bot.updatedAt
        )
        return springDataBotRepository.save(entity).toDomain()
    }

    override fun findById(id: UUID): Bot? {
        return springDataBotRepository.findById(id).orElse(null)?.toDomain()
    }

    override fun findByApiToken(apiToken: String): Bot? {
        return springDataBotRepository.findByApiToken(apiToken)?.toDomain()
    }

    override fun findByOwnerId(ownerId: UUID): List<Bot> {
        return springDataBotRepository.findByOwnerId(ownerId).map { it.toDomain() }
    }

    override fun update(bot: Bot): Bot {
        return springDataBotRepository.findById(bot.id).map { entity ->
            entity.name = bot.name
            entity.description = bot.description
            entity.apiToken = bot.apiToken
            entity.webhookUrl = bot.webhookUrl
            entity.isActive = bot.isActive
            entity.updatedAt = bot.updatedAt
            springDataBotRepository.save(entity).toDomain()
        }.orElseThrow()
    }

    override fun delete(id: UUID) {
        springDataBotRepository.deleteById(id)
    }

    private fun BotJpaEntity.toDomain() = Bot(
        id = id,
        ownerId = ownerId,
        userId = userId,
        name = name,
        description = description,
        apiToken = apiToken,
        webhookUrl = webhookUrl,
        isActive = isActive,
        permissions = permissions.removeSurrounding("[", "]").split(",").map { it.trim().removeSurrounding("\"") },
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
