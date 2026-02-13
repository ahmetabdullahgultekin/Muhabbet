package com.muhabbet.messaging.domain.port.out

import com.muhabbet.messaging.domain.model.Bot
import java.util.UUID

interface BotRepository {
    fun save(bot: Bot): Bot
    fun findById(id: UUID): Bot?
    fun findByApiToken(apiToken: String): Bot?
    fun findByOwnerId(ownerId: UUID): List<Bot>
    fun update(bot: Bot): Bot
    fun delete(id: UUID)
}
